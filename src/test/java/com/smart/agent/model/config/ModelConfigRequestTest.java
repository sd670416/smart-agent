package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelConfigRequestTest {

    /** 记录类型默认 toString 会带明文密钥，必须被显式覆盖为掩码。 */
    @Test
    void masksApiKeyInToString() {
        ModelConfigRequest request = new ModelConfigRequest("测试模型", ModelDeploymentType.CLOUD,
                "https://api.example.com/v1", "test-model", "sk-super-secret-value", Set.of(), 5, 30, 1, "备注");

        String text = request.toString();

        assertThat(text).doesNotContain("sk-super-secret-value").contains("apiKey=****").contains("test-model");
    }

    /** 未配置密钥时保持 null 语义，便于排查「是否填了 Key」。 */
    @Test
    void showsNullWhenApiKeyAbsent() {
        ModelConfigRequest request = new ModelConfigRequest("本地模型", ModelDeploymentType.LOCAL,
                "http://localhost:11434/v1", "qwen", null, Set.of(), 5, 30, 1, null);

        assertThat(request.toString()).contains("apiKey=null");
    }
}
