package com.smart.agent.tool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WebSearchToolTest {
    private static final ToolContext CONTEXT = new ToolContext("tenant", "user", "identity", Set.of());

    @Test
    void sendsOnlyNormalizedPublicQueryToProvider() {
        AtomicReference<WebSearchInput> received = new AtomicReference<>();
        WebSearchProvider provider = input -> {
            received.set(input);
            return new WebSearchResult(input.query(), "2026-09-11T15:00:00+08:00", "晴，最高30摄氏度",
                    List.of(), "test");
        };
        WebSearchTool tool = new WebSearchTool(
                new WebSearchProperties(true, "auto", 5, Duration.ofSeconds(15)),
                new WebSearchPolicy(), provider);

        WebSearchResult result = tool.execute(new WebSearchInput("  天津   今日天气  ", 20, "day"), CONTEXT);

        assertThat(received.get()).isEqualTo(new WebSearchInput("天津 今日天气", 10, "day"));
        assertThat(result.summary()).isEqualTo("晴，最高30摄氏度");
        assertThat(tool.key()).isEqualTo("web.search");
        assertThat(tool.requiredPermission()).isEqualTo("ai:web-search");
        assertThat(tool.risk()).isEqualTo(ToolRisk.L1);
    }

    @Test
    void usesConfiguredResultLimitWhenCallerOmitsIt() {
        AtomicReference<WebSearchInput> received = new AtomicReference<>();
        WebSearchTool tool = new WebSearchTool(
                new WebSearchProperties(true, "auto", 4, Duration.ofSeconds(15)),
                new WebSearchPolicy(), input -> {
                    received.set(input);
                    return new WebSearchResult(input.query(), "now", "result", List.of(), "test");
                });

        tool.execute(new WebSearchInput("公开新闻", null, null), CONTEXT);

        assertThat(received.get()).isEqualTo(new WebSearchInput("公开新闻", 4, null));
    }

    @Test
    void rejectsDisabledSearchBeforeCallingProvider() {
        WebSearchTool tool = new WebSearchTool(
                new WebSearchProperties(false, "auto", 5, Duration.ofSeconds(15)),
                new WebSearchPolicy(), input -> {
                    throw new AssertionError("provider must not be called");
                });

        assertThatThrownBy(() -> tool.execute(new WebSearchInput("天津天气", 5, null), CONTEXT))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_WEB_SEARCH_DISABLED"));
    }

    @Test
    void rejectsInvalidInputBeforeCallingProvider() {
        WebSearchTool tool = new WebSearchTool(
                new WebSearchProperties(true, "auto", 5, Duration.ofSeconds(15)),
                new WebSearchPolicy(), input -> {
                    throw new AssertionError("provider must not be called");
                });

        assertThatThrownBy(() -> tool.execute(new WebSearchInput(" ", 5, null), CONTEXT))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_WEB_SEARCH_INVALID_INPUT"));
    }

    @Test
    void rejectsProviderResponseWithoutAnswerOrReliableSources() {
        WebSearchTool tool = new WebSearchTool(
                new WebSearchProperties(true, "auto", 5, Duration.ofSeconds(15)),
                new WebSearchPolicy(), input -> new WebSearchResult(input.query(), "now", " ", List.of(), "test"));

        assertThatThrownBy(() -> tool.execute(new WebSearchInput("公开信息", 5, null), CONTEXT))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_WEB_SEARCH_NO_RESULTS"));
    }
}
