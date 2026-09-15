package com.smart.agent.model.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.attachment.Attachment;
import com.smart.agent.attachment.AttachmentObjectStorage;
import com.smart.agent.attachment.AttachmentService;
import com.smart.agent.attachment.AttachmentStatus;
import com.smart.agent.security.AgentUserContext;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 执行不包含业务数据的 OpenAI 兼容性测试。 */
@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModelCompatibilityTester {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Semaphore CONCURRENCY = new Semaphore(4);
    private static final Set<Integer> SUCCESS = Set.of(200, 201);

    private final ObjectMapper mapper;
    private final ModelConfigRepository configs;
    private final ModelTestLogRepository logs;
    private final ModelSecretCipher cipher;
    private final ModelTestTransport transport;
    private final AttachmentService attachments;
    private final Optional<AttachmentObjectStorage> objectStorage;

    public ModelCompatibilityTester(ObjectMapper mapper, ModelConfigRepository configs, ModelTestLogRepository logs,
            ModelSecretCipher cipher, ModelTestTransport transport, AttachmentService attachments,
            Optional<AttachmentObjectStorage> objectStorage) {
        this.mapper = mapper;
        this.configs = configs;
        this.logs = logs;
        this.cipher = cipher;
        this.transport = transport;
        this.attachments = attachments;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public ModelTestResult test(ModelConfig config, String optionalImageAttachmentId, AgentUserContext actor) {
        if (!CONCURRENCY.tryAcquire()) {
            throw new IllegalStateException("当前模型测试任务较多，请稍后重试");
        }
        try {
            String apiKey = cipher.decrypt(config.encryptedApiKey());
            String endpoint = completionEndpoint(config.baseUrl());
            List<ModelTestResult.Item> results = new ArrayList<>();
            results.add(execute("BASIC", config, apiKey, endpoint, basicBody(config), this::validateBasic));
            results.add(capability(config, ModelCapability.STREAMING, "STREAMING",
                    () -> execute("STREAMING", config, apiKey, endpoint, streamingBody(config), this::validateStreaming)));
            results.add(capability(config, ModelCapability.TOOL_CALLING, "TOOL_CALLING",
                    () -> execute("TOOL_CALLING", config, apiKey, endpoint, toolBody(config), this::validateTool)));
            results.add(visionResult(config, apiKey, endpoint, optionalImageAttachmentId, actor));

            results.forEach(item -> logs.save(ModelTestLog.from(config.id(), actor.tenantId(), actor.userId(), item, null)));
            String status = results.stream().anyMatch(item -> "FAILED".equals(item.status())) ? "FAILED" : "PASSED";
            String summary = summarize(results);
            config.recordTest(status, summary);
            configs.save(config);
            return new ModelTestResult(config.id(), status, Instant.now(), List.copyOf(results));
        } finally {
            CONCURRENCY.release();
        }
    }

    private ModelTestResult.Item visionResult(ModelConfig config, String apiKey, String endpoint,
            String attachmentId, AgentUserContext actor) {
        if (!config.capabilities().contains(ModelCapability.VISION)) {
            return skipped("VISION", "模型未声明图片能力，已跳过");
        }
        if (attachmentId == null || attachmentId.isBlank()) {
            return skipped("VISION", "未提供测试图片，图片能力暂未验证");
        }
        if (objectStorage.isEmpty()) {
            return skipped("VISION", "当前环境未配置附件存储，图片能力暂未验证");
        }
        try {
            Attachment attachment = attachments.get(UUID.fromString(attachmentId), actor.tenantId(), actor.userId());
            if (attachment.status() != AttachmentStatus.READY
                    || attachment.detectedMediaType() == null
                    || !attachment.detectedMediaType().startsWith("image/")) {
                return failed("VISION", null, 0, "测试附件不是已就绪的图片");
            }
            String dataUrl;
            try (InputStream input = objectStorage.get().open(attachment.objectKey())) {
                dataUrl = "data:" + attachment.detectedMediaType() + ";base64," + Base64.getEncoder().encodeToString(readImage(input));
            }
            return execute("VISION", config, apiKey, endpoint, visionBody(config, dataUrl), this::validateBasic);
        } catch (IllegalArgumentException failure) {
            return failed("VISION", null, 0, "测试图片编号格式不正确");
        } catch (Exception failure) {
            return failed("VISION", null, 0, "无法读取测试图片，请重新上传后再试");
        }
    }

    private ModelTestResult.Item capability(ModelConfig config, ModelCapability capability, String item,
            java.util.function.Supplier<ModelTestResult.Item> test) {
        return config.capabilities().contains(capability) ? test.get() : skipped(item, "模型未声明该能力，已跳过");
    }

    private ModelTestResult.Item execute(String item, ModelConfig config, String apiKey, String endpoint,
            String body, Validator validator) {
        try {
            ModelTestTransport.Response response = transport.post(endpoint, config.deploymentType(), apiKey, body,
                    config.connectTimeout(), config.readTimeout());
            if (!SUCCESS.contains(response.statusCode())) {
                return failed(item, response.statusCode(), response.durationMillis(), httpMessage(response.statusCode()));
            }
            String incompatibility = validator.validate(response.body());
            return incompatibility == null
                    ? new ModelTestResult.Item(item, "PASSED", response.statusCode(), response.durationMillis(), "测试通过")
                    : failed(item, response.statusCode(), response.durationMillis(), incompatibility);
        } catch (JdkModelTestTransport.ModelTestTransportException failure) {
            return failed(item, null, failure.durationMillis(), failure.getMessage());
        } catch (RuntimeException failure) {
            return failed(item, null, 0, "模型响应格式无法识别，请确认服务兼容 OpenAI Chat Completions 协议");
        }
    }

    private String validateBasic(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode message = root.path("choices").path(0).path("message");
            return message.isObject() && (message.has("content") || message.has("reasoning_content"))
                    ? null : "响应中缺少 choices[0].message";
        } catch (Exception failure) {
            return "响应不是有效 JSON";
        }
    }

    private String validateStreaming(String body) {
        return body.contains("data:") && (body.contains("[DONE]") || body.contains("choices"))
                ? null : "响应不符合 OpenAI SSE 流式格式";
    }

    private String validateTool(String body) {
        try {
            JsonNode calls = mapper.readTree(body).path("choices").path(0).path("message").path("tool_calls");
            if (!calls.isArray() || calls.isEmpty()) return "模型未按要求返回工具调用";
            String name = calls.path(0).path("function").path("name").asText();
            return "agent_compatibility_probe".equals(name) ? null : "模型返回了无法识别的工具名称";
        } catch (Exception failure) {
            return "工具调用响应不是有效 JSON";
        }
    }

    private String basicBody(ModelConfig config) {
        return json(Map.of("model", config.modelName(), "messages", List.of(
                Map.of("role", "user", "content", "请只回复：连接正常")), "max_tokens", 16, "stream", false));
    }

    private String streamingBody(ModelConfig config) {
        return json(Map.of("model", config.modelName(), "messages", List.of(
                Map.of("role", "user", "content", "请只回复：流式正常")), "max_tokens", 16, "stream", true));
    }

    private String toolBody(ModelConfig config) {
        Map<String, Object> function = Map.of("name", "agent_compatibility_probe", "description", "模型兼容性测试工具",
                "parameters", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));
        return json(Map.of("model", config.modelName(), "messages", List.of(
                        Map.of("role", "user", "content", "请调用 agent_compatibility_probe 工具，不要输出其他内容")),
                "tools", List.of(Map.of("type", "function", "function", function)),
                "tool_choice", Map.of("type", "function", "function", Map.of("name", "agent_compatibility_probe")),
                "max_tokens", 64, "stream", false));
    }

    private String visionBody(ModelConfig config, String dataUrl) {
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", "请只回复：图片正常"),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        return json(Map.of("model", config.modelName(), "messages", List.of(Map.of("role", "user", "content", content)),
                "max_tokens", 16, "stream", false));
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

    private static ModelTestResult.Item skipped(String item, String message) {
        return new ModelTestResult.Item(item, "SKIPPED", null, 0, message);
    }

    private static ModelTestResult.Item failed(String item, Integer httpStatus, long duration, String message) {
        return new ModelTestResult.Item(item, "FAILED", httpStatus, duration, message);
    }

    private static String summarize(List<ModelTestResult.Item> items) {
        long passed = items.stream().filter(item -> "PASSED".equals(item.status())).count();
        long failed = items.stream().filter(item -> "FAILED".equals(item.status())).count();
        long skipped = items.size() - passed - failed;
        return "通过 " + passed + " 项，失败 " + failed + " 项，跳过 " + skipped + " 项";
    }

    private static byte[] readImage(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > MAX_IMAGE_BYTES) throw new IllegalArgumentException("测试图片不能超过 5MB");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface Validator {
        String validate(String body);
    }
}
