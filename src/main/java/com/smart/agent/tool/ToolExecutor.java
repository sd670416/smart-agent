package com.smart.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(AgentRunService.class)
public class ToolExecutor implements AutoCloseable {
    static final int MAX_RESULT_BYTES = 64 * 1024;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

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
        AgentTool<?, ?> tool = toolRegistry.require(toolKey);
        long startedAt = System.nanoTime();
        try {
            ToolContext toolContext = ToolContext.from(userContext);
            if (!userContext.permissions().contains(tool.requiredPermission())) {
                throw failure("AGENT_TOOL_FORBIDDEN", HttpStatus.FORBIDDEN, "Tool permission is required", tool, startedAt,
                        ToolExecutionOutcome.DENIED, 0);
            }
            Object typedInput = deserializeInput(input, tool.inputType());
            validateProjectScope(tool, typedInput, toolContext, startedAt);
            Object result = executeWithTimeout(tool, typedInput, toolContext);
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
    private Object executeWithTimeout(AgentTool<?, ?> rawTool, Object input, ToolContext context)
            throws InterruptedException, TimeoutException {
        AgentTool<Object, Object> tool = (AgentTool<Object, Object>) rawTool;
        Future<Object> future = executorService.submit(() -> tool.execute(input, context));
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            throw new ToolExecutionException();
        }
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
