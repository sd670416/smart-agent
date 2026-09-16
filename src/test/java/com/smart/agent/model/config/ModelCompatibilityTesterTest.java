package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.security.AgentUserContext;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModelCompatibilityTesterTest {
    private final ModelConfigRepository configs = mock(ModelConfigRepository.class);
    private final ModelTestLogRepository logs = mock(ModelTestLogRepository.class);
    private final AgentUserContext actor = new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of(), Set.of());

    @Test
    void sendsFixedPromptAndReturnsModelReplyText() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        ModelTestTransport transport = (url, deployment, key, body, connect, read) -> {
            requestBody.set(body);
            return new ModelTestTransport.Response(200,
                    "{\"choices\":[{\"message\":{\"content\":\"Hi there! I'm the GLM model.\"}}]}", 12);
        };

        ModelTestResult result = tester(transport).test(model(), actor);

        assertThat(requestBody.get()).contains("\"hi\"").contains("\"stream\":false");
        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.items()).hasSize(1);
        ModelTestResult.Item item = result.items().get(0);
        assertThat(item.testItem()).isEqualTo("CHAT");
        assertThat(item.message()).isEqualTo("Hi there! I'm the GLM model.");
        assertThat(item.httpStatus()).isEqualTo(200);
        verify(logs, times(1)).save(any(ModelTestLog.class));
        verify(configs).save(any(ModelConfig.class));
    }

    @Test
    void recordsSuccessSummaryOnTheModel() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) ->
                new ModelTestTransport.Response(200, "{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}", 10);
        ModelConfig config = model();

        tester(transport).test(config, actor);

        assertThat(config.lastTestStatus()).isEqualTo("PASSED");
        assertThat(config.lastTestSummary()).isEqualTo("测试成功");
    }

    @Test
    void returnsFriendlyAuthenticationFailureWithoutExposingProviderBody() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) ->
                new ModelTestTransport.Response(401, "{\"error\":\"secret provider detail\"}", 8);

        ModelTestResult result = tester(transport).test(model(), actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).message()).contains("API Key").doesNotContain("secret provider detail");
        verify(configs).save(any(ModelConfig.class));
    }

    @Test
    void explainsMissingEndpointOrModelFor404() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) ->
                new ModelTestTransport.Response(404, "not found", 9);

        ModelTestResult result = tester(transport).test(model(), actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).message()).contains("服务地址").contains("模型名称");
    }

    @Test
    void reportsMissingReplyContentAsCompatibilityFailure() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) ->
                new ModelTestTransport.Response(200, "{\"choices\":[{\"message\":{}}]}", 15);

        ModelTestResult result = tester(transport).test(model(), actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).message()).contains("缺少").contains("content");
    }

    @Test
    void returnsFriendlyTimeoutResult() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) -> {
            throw new JdkModelTestTransport.ModelTestTransportException(
                    "连接模型服务超时，请检查地址或调整超时时间", new java.net.http.HttpTimeoutException("timeout"), 5000);
        };

        ModelTestResult result = tester(transport).test(model(), actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).durationMillis()).isEqualTo(5000);
        assertThat(result.items().get(0).message()).contains("超时");
    }

    /** 响应体超过 1MB 缓冲上限时要给出真实原因，不能落进「格式无法识别」分支。 */
    @Test
    void explainsResponseSizeLimitInsteadOfFormatFailure() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) -> {
            throw new IllegalStateException("decode failed",
                    new org.springframework.core.io.buffer.DataBufferLimitException("Exceeded limit on max bytes"));
        };

        ModelTestResult result = tester(transport).test(model(), actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).message()).contains("1MB").doesNotContain("格式无法识别");
    }

    private ModelCompatibilityTester tester(ModelTestTransport transport) {
        when(logs.save(any(ModelTestLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new ModelCompatibilityTester(new ObjectMapper(), configs, logs,
                new PlainTextModelSecretCipher(), transport);
    }

    private static ModelConfig model() {
        return ModelConfig.create("测试模型", ModelProviderType.OPENAI_COMPATIBLE, ModelDeploymentType.CLOUD,
                "https://api.example.com/v1", "test-model", "sk-test", Set.of(),
                Duration.ofSeconds(5), Duration.ofSeconds(30), 1, null);
    }
}
