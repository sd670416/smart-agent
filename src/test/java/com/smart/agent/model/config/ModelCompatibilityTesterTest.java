package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.attachment.AttachmentService;
import com.smart.agent.security.AgentUserContext;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelCompatibilityTesterTest {
    private final ModelConfigRepository configs = mock(ModelConfigRepository.class);
    private final ModelTestLogRepository logs = mock(ModelTestLogRepository.class);
    private final AttachmentService attachments = mock(AttachmentService.class);
    private final AgentUserContext actor = new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of(), Set.of());

    @Test
    void passesBasicTestAndSkipsCapabilitiesNotDeclaredByModel() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) ->
                new ModelTestTransport.Response(200, "{\"choices\":[{\"message\":{\"content\":\"连接正常\"}}]}", 12);
        ModelCompatibilityTester tester = tester(transport);

        ModelTestResult result = tester.test(model(Set.of()), null, actor);

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.items()).extracting(ModelTestResult.Item::status)
                .containsExactly("PASSED", "SKIPPED", "SKIPPED", "SKIPPED");
        verify(logs, times(4)).save(any(ModelTestLog.class));
        verify(configs).save(any(ModelConfig.class));
    }

    @Test
    void returnsFriendlyAuthenticationFailureWithoutExposingProviderBody() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) ->
                new ModelTestTransport.Response(401, "{\"error\":\"secret provider detail\"}", 8);

        ModelTestResult result = tester(transport).test(model(Set.of()), null, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).message()).contains("API Key").doesNotContain("secret provider detail");
    }

    @Test
    void explainsMissingEndpointOrModelFor404() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) ->
                new ModelTestTransport.Response(404, "not found", 9);

        ModelTestResult result = tester(transport).test(model(Set.of()), null, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).message()).contains("服务地址").contains("模型名称");
    }

    @Test
    void reportsUnsupportedToolResponseAsCompatibilityFailure() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) -> {
            if (body.contains("agent_compatibility_probe")) {
                return new ModelTestTransport.Response(200,
                        "{\"choices\":[{\"message\":{\"content\":\"没有调用工具\"}}]}", 15);
            }
            return new ModelTestTransport.Response(200,
                    "{\"choices\":[{\"message\":{\"content\":\"正常\"}}]}", 10);
        };

        ModelTestResult result = tester(transport).test(model(Set.of(ModelCapability.TOOL_CALLING)), null, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items()).filteredOn(item -> "TOOL_CALLING".equals(item.testItem()))
                .singleElement().extracting(ModelTestResult.Item::message).asString().contains("未按要求返回工具调用");
    }

    @Test
    void returnsFriendlyTimeoutResult() {
        ModelTestTransport transport = (url, deployment, key, body, connect, read) -> {
            throw new JdkModelTestTransport.ModelTestTransportException(
                    "连接模型服务超时，请检查地址或调整超时时间", new java.net.http.HttpTimeoutException("timeout"), 5000);
        };

        ModelTestResult result = tester(transport).test(model(Set.of()), null, actor);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.items().get(0).durationMillis()).isEqualTo(5000);
        assertThat(result.items().get(0).message()).contains("超时");
    }

    private ModelCompatibilityTester tester(ModelTestTransport transport) {
        when(logs.save(any(ModelTestLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new ModelCompatibilityTester(new ObjectMapper(), configs, logs, new PlainTextModelSecretCipher(),
                transport, attachments, Optional.empty());
    }

    private static ModelConfig model(Set<ModelCapability> capabilities) {
        return ModelConfig.create("测试模型", ModelProviderType.OPENAI_COMPATIBLE, ModelDeploymentType.CLOUD,
                "https://api.example.com/v1", "test-model", "sk-test", capabilities,
                Duration.ofSeconds(5), Duration.ofSeconds(30), 1, null);
    }
}
