package com.smart.agent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 覆盖运行期私有凭据上下文的语义：
 * 只在线程内可见、嵌套可恢复、异常后必须清理、绝不把密钥写进任何输出。
 */
class ModelRunContextTest {
    private static final ModelCredentialSource.Credentials OLD =
            new ModelCredentialSource.Credentials("https://old.example.com/v1", "sk-old-secret", "glm-4.5");
    private static final ModelCredentialSource.Credentials NEW =
            new ModelCredentialSource.Credentials("https://new.example.com/v1", "sk-new-secret", "glm-4.6");

    @AfterEach
    void clearsContext() {
        // 断言清理逻辑生效，避免测试之间互相污染。
        assertThat(ModelRunContext.isActive()).isFalse();
    }

    @Test
    void exposesCredentialsOnlyInsideTheRunScope() {
        assertThat(ModelRunContext.credentials()).isNull();

        String observed = ModelRunContext.with(OLD, () -> ModelRunContext.credentials().modelName());

        assertThat(observed).isEqualTo("glm-4.5");
        assertThat(ModelRunContext.credentials()).isNull();
    }

    @Test
    void restoresThePreviousScopeAfterNestedRuns() {
        ModelRunContext.with(OLD, () -> {
            ModelRunContext.with(NEW, () -> assertThat(ModelRunContext.credentials().modelName()).isEqualTo("glm-4.6"));
            assertThat(ModelRunContext.credentials().modelName()).isEqualTo("glm-4.5");
            return null;
        });
    }

    @Test
    void clearsTheContextEvenWhenTheActionFails() {
        assertThatThrownBy(() -> ModelRunContext.with(OLD, () -> {
            throw new IllegalStateException("工具执行失败");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ModelRunContext.credentials()).isNull();
    }

    @Test
    void treatsNullCredentialsAsAnExplicitlyEmptyScope() {
        ModelRunContext.with(OLD, () -> {
            ModelRunContext.with(null, () -> {
                assertThat(ModelRunContext.credentials()).isNull();
                return null;
            });
            // 内层显式清除后，外层上下文必须恢复。
            assertThat(ModelRunContext.credentials().modelName()).isEqualTo("glm-4.5");
            return null;
        });
    }

    @Test
    void neverPrintsTheApiKeyWhenCredentialsAreLogged() {
        String rendered = OLD.toString();

        assertThat(rendered).doesNotContain("sk-old-secret");
        assertThat(rendered).contains("hasApiKey=true", "glm-4.5");
    }
}
