package com.smart.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.security.AgentUserContext;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolContextTest {

    @Test
    void preservesTrustedRolesPermissionsAndProjects() {
        AgentUserContext userContext = new AgentUserContext(
                "tenant", "user", "identity",
                Set.of("menu:project:base", "menu:project:contract"),
                Set.of("project-1"), Set.of("space-1"), Set.of("role-1"));

        ToolContext context = ToolContext.from(userContext);

        assertThat(context.roleIds()).containsExactly("role-1");
        assertThat(context.projectIds()).containsExactly("project-1");
        assertThat(context.permissions())
                .containsExactlyInAnyOrder("menu:project:base", "menu:project:contract");
    }

    @Test
    void keepsLegacyFourArgumentConstructorCompatible() {
        ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("project-1"));

        assertThat(context.projectIds()).containsExactly("project-1");
        assertThat(context.roleIds()).isEmpty();
        assertThat(context.permissions()).isEmpty();
        assertThat(context.modelBinding()).isNull();
    }

    @Test
    void carriesNoModelBindingByDefaultAndAttachesOneOnDemand() {
        ToolContext context = ToolContext.from(new AgentUserContext(
                "tenant", "user", "identity", Set.of(), Set.of("project-1"), Set.of(), Set.of()));

        assertThat(context.modelBindingOptional()).isEmpty();

        ToolContext.ModelBinding binding = new ToolContext.ModelBinding(
                "model-1", "智谱GLM", "glm-4.5", "https://open.bigmodel.cn/api/paas/v4",
                Set.of(com.smart.agent.model.config.ModelCapability.WEB_SEARCH));
        ToolContext bound = context.withModelBinding(binding);

        assertThat(bound.modelBindingOptional()).contains(binding);
        assertThat(bound.modelBinding().supportsWebSearch()).isTrue();
        // 附加绑定不应改变权限与项目范围。
        assertThat(bound.permissions()).isEqualTo(context.permissions());
        assertThat(bound.projectIds()).isEqualTo(context.projectIds());
    }

    @Test
    void treatsUndeclaredCapabilitiesAsUnsupported() {
        ToolContext.ModelBinding binding = new ToolContext.ModelBinding(
                "model-1", "纯对话模型", "chat-only", "https://api.example.com/v1", null);

        assertThat(binding.capabilities()).isEmpty();
        assertThat(binding.supportsWebSearch()).isFalse();
    }
}
