package com.smart.agent.tool.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.ToolRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TimeToolConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TimeToolConfiguration.class, ToolRegistry.class);

    @Test
    void registersCurrentTimeToolAsLevelZeroTool() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CurrentTimeTool.class);
            ToolRegistry registry = context.getBean(ToolRegistry.class);
            AgentUserContext userContext = new AgentUserContext(
                    "tenant-1", "user-1", "identity-1", Set.of(), Set.of());

            assertThat(registry.require("system.current_time"))
                    .isSameAs(context.getBean(CurrentTimeTool.class));
            assertThat(registry.allowedReadOnlyTools(userContext))
                    .extracting(tool -> tool.key())
                    .containsExactly("system.current_time");
        });
    }
}
