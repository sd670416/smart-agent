package com.smart.agent.model.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.security.AgentUserContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行不包含业务数据的 OpenAI 兼容性测试。
 *
 * <p>固定发送一条消息「hi」，把模型的回复文本原样带回给前端展示；
 * 只验证连通性与响应格式，不携带任何业务数据。
 */
@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModelCompatibilityTester {
    private static final String TEST_PROMPT = "hi";
    private static final int MAX_REPLY_CHARS = 2000;
    private static final Semaphore CONCURRENCY = new Semaphore(4);
    private static final Set<Integer> SUCCESS = Set.of(200, 201);

    private final ObjectMapper mapper;
    private final ModelConfigRepository configs;
    private final ModelTestLogRepository logs;
    private final ModelSecretCipher cipher;
    private final ModelTestTransport transport;

    public ModelCompatibilityTester(ObjectMapper mapper, ModelConfigRepository configs, ModelTestLogRepository logs,
            ModelSecretCipher cipher, ModelTestTransport transport) {
        this.mapper = mapper;
        this.configs = configs;
        this.logs = logs;
        this.cipher = cipher;
        this.transport = transport;
    }

    @Transactional
    public ModelTestResult test(ModelConfig config, AgentUserContext actor) {
        if (!CONCURRENCY.tryAcquire()) {
            throw new IllegalStateException("当前模型测试任务较多，请稍后重试");
        }
        try {
            String apiKey = cipher.decrypt(config.encryptedApiKey());
            String endpoint = completionEndpoint(config.baseUrl());
            ModelTestResult.Item item = execute(config, apiKey, endpoint);
            logs.save(ModelTestLog.from(config.id(), actor.tenantId(), actor.userId(), item, null));
            config.recordTest(item.status(), "PASSED".equals(item.status()) ? "测试成功" : item.message());
            configs.save(config);
            return new ModelTestResult(config.id(), item.status(), Instant.now(), List.of(item));
        } finally {
            CONCURRENCY.release();
        }
    }

    private ModelTestResult.Item execute(ModelConfig config, String apiKey, String endpoint) {
        String body = json(Map.of("model", config.modelName(),
                "messages", List.of(Map.of("role", "user", "content", TEST_PROMPT)),
                "max_tokens", 256, "stream", false));
        try {
            ModelTestTransport.Response response = transport.post(endpoint, config.deploymentType(), apiKey, body,
                    config.connectTimeout(), config.readTimeout());
            if (!SUCCESS.contains(response.statusCode())) {
                return failed(response.statusCode(), response.durationMillis(), httpMessage(response.statusCode()));
            }
            String reply = extractReply(response.body());
            return reply == null
                    ? failed(response.statusCode(), response.durationMillis(), "响应中缺少 choices[0].message.content，无法获取模型回复")
                    : new ModelTestResult.Item("CHAT", "PASSED", response.statusCode(), response.durationMillis(), reply);
        } catch (JdkModelTestTransport.ModelTestTransportException failure) {
            return failed(null, failure.durationMillis(), failure.getMessage());
        } catch (RuntimeException failure) {
            if (responseTooLarge(failure)) {
                return failed(null, 0, "模型响应内容超过 1MB 上限，请确认服务地址指向 OpenAI 兼容的 Chat Completions 接口");
            }
            return failed(null, 0, "模型响应格式无法识别，请确认服务兼容 OpenAI Chat Completions 协议");
        }
    }

    /**
     * 判断异常链中是否包含响应体超限。
     *
     * <p>响应超过 {@code maxInMemorySize} 时 WebClient 抛 {@code DataBufferLimitException}，
     * 若不加识别会落进「格式无法识别」分支，给出与实际原因无关的中文提示。</p>
     */
    private static boolean responseTooLarge(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataBufferLimitException) {
                return true;
            }
        }
        return false;
    }

    /** 从响应中提取模型回复文本；content 为空时回退 reasoning_content，两者都没有返回 null。 */
    private String extractReply(String body) {
        try {
            JsonNode message = mapper.readTree(body).path("choices").path(0).path("message");
            JsonNode content = message.path("content");
            String text = content.isTextual() ? content.asText() : message.path("reasoning_content").asText(null);
            return text == null || text.isBlank() ? null : truncate(text);
        } catch (Exception failure) {
            return null;
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_REPLY_CHARS ? value : value.substring(0, MAX_REPLY_CHARS);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("无法生成模型测试请求", failure);
        }
    }

    private static String completionEndpoint(String baseUrl) {
        String value = baseUrl.replaceAll("/+$", "");
        return value.endsWith("/chat/completions") ? value : value + "/chat/completions";
    }

    private static String httpMessage(int status) {
        return switch (status) {
            case 401, 403 -> "认证失败，请检查 API Key 和模型访问权限";
            case 404 -> "接口或模型不存在，请检查服务地址和模型名称";
            case 408, 504 -> "模型服务响应超时";
            case 429 -> "模型服务请求过于频繁或额度不足";
            default -> status >= 500 ? "模型服务内部异常（HTTP " + status + "）" : "模型服务拒绝请求（HTTP " + status + "）";
        };
    }

    private static ModelTestResult.Item failed(Integer httpStatus, long duration, String message) {
        return new ModelTestResult.Item("CHAT", "FAILED", httpStatus, duration, message);
    }
}
