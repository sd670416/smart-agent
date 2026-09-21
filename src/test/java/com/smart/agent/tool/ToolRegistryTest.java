package com.smart.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.security.AgentUserContext;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
    @Test
    void allowsReadOnlyToolThatDoesNotRequireAdditionalPermission() {
        AgentTool<Object, Object> tool = new AgentTool<>() {
            @Override public String key() { return "approval.query"; }
            @Override public Class<Object> inputType() { return Object.class; }
            @Override public String requiredPermission() { return ""; }
            @Override public ToolRisk risk() { return ToolRisk.L1; }
            @Override public Object execute(Object input, ToolContext context) { return input; }
        };
        AgentUserContext user = new AgentUserContext(
                "tenant", "user", "identity", Set.of(), Set.of(), Set.of(), Set.of());

        assertThat(new ToolRegistry(Set.of(tool)).allowedReadOnlyTools(user)).containsExactly(tool);
    }
}
