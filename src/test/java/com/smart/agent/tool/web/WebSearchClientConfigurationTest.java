package com.smart.agent.tool.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.ToolRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebSearchClientConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(WebSearchClientConfiguration.class, ToolRegistry.class)
            .withPropertyValues(
                    "agent.web-search.enabled=true",
                    "agent.web-search.provider=openai",
                    "agent.model.base-url=http://localhost:9999/v1",
                    "agent.model.api-key=test-key",
                    "agent.model.chat-model=gpt-5");

    @Test
    void registersRoutedWebSearchOnlyForUsersWithPermission() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenAiWebSearchProvider.class);
            assertThat(context).hasSingleBean(ZhipuWebSearchProvider.class);
            assertThat(context).hasSingleBean(WebSearchProviderRouter.class);
            assertThat(context).hasSingleBean(WebSearchTool.class);
            ToolRegistry registry = context.getBean(ToolRegistry.class);

            assertThat(registry.allowedReadOnlyTools(user(Set.of())))
                    .extracting(tool -> tool.key()).doesNotContain("web.search");
            assertThat(registry.allowedReadOnlyTools(user(Set.of("ai:web-search"))))
                    .extracting(tool -> tool.key()).contains("web.search");
        });
    }

    private AgentUserContext user(Set<String> permissions) {
        return new AgentUserContext("tenant", "user", "identity", permissions, Set.of());
    }
}
