package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 测试传输层的失败映射。
 *
 * <p>只覆盖不依赖真实网络的分支：这些分支此前会把具体原因吞成「无法连接模型服务」，
 * 让用户看不到真实错误。</p>
 */
class JdkModelTestTransportTest {
    private final JdkModelTestTransport transport = new JdkModelTestTransport();

    /**
     * 测试链路不再按部署方式限制内网地址，与运行时聊天链路保持一致。
     *
     * <p>历史上 CLOUD 会拒绝内网地址，导致「聊天能用、点测试报错」；现在应当直接尝试连接。</p>
     */
    @Test
    void doesNotRestrictInternalTargetForAnyDeployment() {
        for (ModelDeploymentType deploymentType : new ModelDeploymentType[] {
                ModelDeploymentType.CLOUD, ModelDeploymentType.LOCAL }) {
            assertThatThrownBy(() -> transport.post("http://127.0.0.1:1/v1", deploymentType,
                    "sk-test", "{}", Duration.ofSeconds(2), Duration.ofSeconds(2)))
                    .isInstanceOf(JdkModelTestTransport.ModelTestTransportException.class)
                    .hasMessageNotContaining("内部网络");
        }
    }

    /** 地址格式错误仍要给出可读提示，不能被泛化的网络错误掩盖。 */
    @Test
    void keepsReadableMessageForMalformedAddress() {
        assertThatThrownBy(() -> transport.post("not-a-valid-url", ModelDeploymentType.CLOUD,
                "sk-test", "{}", Duration.ofSeconds(2), Duration.ofSeconds(2)))
                .isInstanceOf(JdkModelTestTransport.ModelTestTransportException.class)
                .hasMessageNotContaining("内部网络")
                .hasMessageContaining("主机名");
    }

    /** 失败结果里不允许出现接口密钥（含整条异常链，链上异常 message 可能为 null）。 */
    @Test
    void neverLeaksApiKeyInFailureMessage() {
        assertThatThrownBy(() -> transport.post("http://127.0.0.1:1/v1", ModelDeploymentType.CLOUD,
                "sk-super-secret", "{}", Duration.ofSeconds(2), Duration.ofSeconds(2)))
                .isInstanceOf(JdkModelTestTransport.ModelTestTransportException.class)
                .satisfies(failure -> {
                    StringBuilder text = new StringBuilder(String.valueOf(failure.getMessage()));
                    for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
                        text.append(' ').append(cause.getMessage());
                    }
                    assertThat(text.toString()).doesNotContain("sk-super-secret");
                });
    }
}
