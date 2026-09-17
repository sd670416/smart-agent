package com.smart.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelCredentialSource;
import com.smart.agent.model.ModelRunContext;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ToolExecutor implements AutoCloseable {
    static final int MAX_RESULT_BYTES = 64 * 1024;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration PROJECT_ARCHIVE_TIMEOUT = Duration.ofSeconds(30);

    private final ToolRegistry toolRegistry;
    private final AgentRunService agentRunService;
    private final ObjectMapper strictObjectMapper;
    private final ExecutorService executorService;
    private final Duration timeout;

    @Autowired
    public ToolExecutor(ToolRegistry toolRegistry, AgentRunService agentRunService) {
        this(toolRegistry, agentRunService, DEFAULT_TIMEOUT);
    }

    public ToolExecutor(ToolRegistry toolRegistry, AgentRunService agentRunService, Duration timeout) {
        this(toolRegistry, agentRunService, timeout, Executors.newVirtualThreadPerTaskExecutor());
    }

    ToolExecutor(
            ToolRegistry toolRegistry,
            AgentRunService agentRunService,
            Duration timeout,
            ExecutorService executorService) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.toolRegistry = toolRegistry;
        this.agentRunService = agentRunService;
        this.timeout = timeout;
        this.executorService = executorService;
        this.strictObjectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public Object execute(String toolKey, Object input, AgentUserContext userContext) {
        return execute(toolKey, input, userContext, null);
    }

    /**
     * 带本轮模型绑定执行工具。
     *
     * @param modelBinding 本轮运行固定的模型绑定；为 {@code null} 时工具按无模型上下文执行，
     *                     依赖模型的能力（如联网搜索）会给出"请切换模型"的引导而非静默失败
     */
    public Object execute(String toolKey, Object input, AgentUserContext userContext,
            ToolContext.ModelBinding modelBinding) {
        return execute(toolKey, input, userContext, modelBinding, null);
    }

    /**
     * 带本轮模型绑定与运行期私有凭据执行工具。
     *
     * @param modelBinding 本轮运行固定的模型绑定，见上一个重载
     * @param credentials  执行句柄持有的模型凭据，与绑定来自同一配置版本。
     *                     它只在执行工具的工作线程内可见：<b>不写入 ToolContext、不落日志、
     *                     不进调试事件、不参与序列化</b>；{@code null} 表示本轮无可用凭据
     */
    public Object execute(String toolKey, Object input, AgentUserContext userContext,
            ToolContext.ModelBinding modelBinding, ModelCredentialSource.Credentials credentials) {
        AgentTool<?, ?> tool = toolRegistry.require(toolKey);
        long startedAt = System.nanoTime();
        try {
            ToolContext toolContext = ToolContext.from(userContext);
            if (modelBinding != null) {
                toolContext = toolContext.withModelBinding(modelBinding);
            }
            if (tool.risk() != ToolRisk.L0 && !userContext.permissions().contains(tool.requiredPermission())) {
                String forbiddenCode = tool.key().startsWith("project.")
                        ? "AGENT_PROJECT_MENU_FORBIDDEN"
                        : "web.search".equals(tool.key())
                                ? "AGENT_WEB_SEARCH_FORBIDDEN" : "AGENT_TOOL_FORBIDDEN";
                throw failure(forbiddenCode, HttpStatus.FORBIDDEN, "Tool permission is required", tool, startedAt,
                        ToolExecutionOutcome.DENIED, 0);
            }
            Object typedInput = deserializeInput(input, tool.inputType());
            validateProjectScope(tool, typedInput, toolContext, startedAt);
            Object result = executeWithTimeout(tool, typedInput, toolContext, credentials);
            int resultSizeBytes = serializeResultSize(result);
            record(tool, ToolExecutionOutcome.SUCCEEDED, startedAt, resultSizeBytes);
            return result;
        } catch (ToolFailureException exception) {
            throw exception.agentException();
        } catch (AgentException exception) {
            record(tool, ToolExecutionOutcome.FAILED, startedAt, 0);
            throw exception;
        } catch (InvalidInputException exception) {
            record(tool, ToolExecutionOutcome.INVALID_INPUT, startedAt, 0);
            throw new AgentException("AGENT_TOOL_INVALID_INPUT", HttpStatus.BAD_REQUEST, "Invalid tool input");
        } catch (ResultTooLargeException exception) {
            record(tool, ToolExecutionOutcome.RESULT_TOO_LARGE, startedAt, exception.resultSizeBytes());
            throw new AgentException("AGENT_TOOL_RESULT_TOO_LARGE", HttpStatus.BAD_GATEWAY, "Tool result exceeds size limit");
        } catch (TimeoutException exception) {
            record(tool, ToolExecutionOutcome.TIMED_OUT, startedAt, 0);
            throw new AgentException("AGENT_TOOL_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, "Tool execution timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            record(tool, ToolExecutionOutcome.FAILED, startedAt, 0);
            throw new AgentException("AGENT_TOOL_INTERRUPTED", HttpStatus.SERVICE_UNAVAILABLE, "Tool execution interrupted");
        } catch (Exception exception) {
            record(tool, ToolExecutionOutcome.FAILED, startedAt, 0);
            throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "Tool execution failed");
        }
    }

    private Object deserializeInput(Object input, Class<?> inputType) {
        try {
            return strictObjectMapper.readValue(strictObjectMapper.writeValueAsBytes(input), inputType);
        } catch (Exception exception) {
            throw new InvalidInputException();
        }
    }

    @SuppressWarnings("unchecked")
    private void validateProjectScope(AgentTool<?, ?> rawTool, Object input, ToolContext context, long startedAt) {
        AgentTool<Object, ?> tool = (AgentTool<Object, ?>) rawTool;
        tool.projectId(input).ifPresent(projectId -> {
            if (!context.canAccessProject(projectId)) {
                throw failure("AGENT_TOOL_PROJECT_FORBIDDEN", HttpStatus.FORBIDDEN, "Tool project scope is not permitted", rawTool,
                        startedAt, ToolExecutionOutcome.DENIED, 0);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Object executeWithTimeout(AgentTool<?, ?> rawTool, Object input, ToolContext context,
            ModelCredentialSource.Credentials credentials) throws InterruptedException, TimeoutException {
        AgentTool<Object, Object> tool = (AgentTool<Object, Object>) rawTool;
        // 在工作线程内建立运行期私有上下文：线程私有，工具执行完（含异常）立即清理。
        Future<Object> future = executorService.submit(
                () -> ModelRunContext.with(credentials, () -> tool.execute(input, context)));
        try {
            Duration executionTimeout = timeoutFor(tool.key());
            return future.get(executionTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof AgentException agentException) {
                throw agentException;
            }
            throw new ToolExecutionException();
        }
    }

    Duration timeoutFor(String toolKey) {
        return "project.getArchiveDetail".equals(toolKey) ? PROJECT_ARCHIVE_TIMEOUT : timeout;
    }

    private int serializeResultSize(Object result) {
        if (result == null) {
            throw new ToolExecutionException();
        }
        try {
            int resultSizeBytes = strictObjectMapper.writeValueAsBytes(result).length;
            if (resultSizeBytes > MAX_RESULT_BYTES) {
                throw new ResultTooLargeException(resultSizeBytes);
            }
            return resultSizeBytes;
        } catch (JsonProcessingException exception) {
            throw new ToolExecutionException();
        }
    }

    private ToolFailureException failure(
            String code,
            HttpStatus status,
            String message,
            AgentTool<?, ?> tool,
            long startedAt,
            ToolExecutionOutcome outcome,
            int resultSizeBytes) {
        record(tool, outcome, startedAt, resultSizeBytes);
        return new ToolFailureException(new AgentException(code, status, message));
    }

    private void record(AgentTool<?, ?> tool, ToolExecutionOutcome outcome, long startedAt, int resultSizeBytes) {
        long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        try {
            agentRunService.recordToolExecution(
                    new ToolExecutionSummary(tool.key(), tool.risk(), outcome, durationMillis, resultSizeBytes));
        } catch (RuntimeException ignored) {
            // Tool execution must not expose or depend on the local Phase 1 summary sink.
        }
    }

    @Override
    @PreDestroy
    public void close() {
        executorService.shutdownNow();
    }

    private static final class InvalidInputException extends RuntimeException {}

    private static final class ResultTooLargeException extends RuntimeException {
        private final int resultSizeBytes;

        private ResultTooLargeException(int resultSizeBytes) {
            this.resultSizeBytes = resultSizeBytes;
        }

        int resultSizeBytes() {
            return resultSizeBytes;
        }
    }

    private static final class ToolExecutionException extends RuntimeException {}

    private static final class ToolFailureException extends RuntimeException {
        private final AgentException agentException;

        private ToolFailureException(AgentException agentException) {
            this.agentException = agentException;
        }

        AgentException agentException() {
            return agentException;
        }
    }
}
