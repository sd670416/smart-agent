package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelConfigTest {
    @Test
    void storesCapabilitiesAndIncrementsVersionWhenConfigurationChanges() {
        ModelConfig config = ModelConfig.create("智谱", ModelProviderType.OPENAI_COMPATIBLE,
                ModelDeploymentType.CLOUD, "https://example.com/v1", "glm-test", "cipher",
                Set.of(ModelCapability.STREAMING, ModelCapability.TOOL_CALLING),
                Duration.ofSeconds(10), Duration.ofSeconds(120), 10, "测试");

        config.update("智谱新版", ModelDeploymentType.CLOUD, "https://example.com/v1", "glm-new", null,
                Set.of(ModelCapability.STREAMING), Duration.ofSeconds(5), Duration.ofSeconds(60), 20, null);

        assertThat(config.capabilities()).containsExactly(ModelCapability.STREAMING);
        assertThat(config.configVersion()).isEqualTo(2L);
        assertThat(config.encryptedApiKey()).isEqualTo("cipher");
    }

    @Test
    void defaultModelMustRemainEnabled() {
        ModelConfig config = ModelConfig.create("本地模型", ModelProviderType.OPENAI_COMPATIBLE,
                ModelDeploymentType.LOCAL, "http://127.0.0.1:11434/v1", "local", null,
                Set.of(ModelCapability.STREAMING), Duration.ofSeconds(5), Duration.ofSeconds(30), 1, null);
        config.makeDefault();

        assertThatThrownBy(() -> config.setEnabled(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default model");
    }
}
