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
    }
}
