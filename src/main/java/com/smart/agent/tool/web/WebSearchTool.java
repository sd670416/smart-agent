package com.smart.agent.tool.web;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class WebSearchTool implements AgentTool<WebSearchInput, WebSearchResult> {
    private static final Set<String> FRESHNESS_VALUES = Set.of("day", "week", "month", "year");
    private static final String SCHEMA = """
            {"type":"object","properties":{
              "query":{"type":"string","description":"仅包含公开互联网信息的搜索词，最多300字"},
              "maxResults":{"type":"integer","description":"期望来源数量，范围1到10"},
              "freshness":{"type":"string","description":"可选时效范围：day、week、month或year"}
            },"required":["query"],"additionalProperties":false}
            """;

    private final WebSearchProperties properties;
    private final WebSearchPolicy policy;
    private final WebSearchProvider provider;

    public WebSearchTool(WebSearchProperties properties, WebSearchPolicy policy, WebSearchProvider provider) {
        this.properties = properties;
        this.policy = policy;
        this.provider = provider;
    }

    @Override public String key() { return "web.search"; }
    @Override public Class<WebSearchInput> inputType() { return WebSearchInput.class; }
    @Override public String requiredPermission() { return "ai:web-search"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "查询天气、新闻、政策法规等公开互联网信息；不得用于内部项目、合同、人员、附件或权限数据";
    }
    @Override public String argumentsSchemaJson() { return SCHEMA; }

    @Override
    public WebSearchResult execute(WebSearchInput input, ToolContext context) {
        if (!properties.enabled()) {
            throw new AgentException("AGENT_WEB_SEARCH_DISABLED", HttpStatus.SERVICE_UNAVAILABLE,
                    "Web search is disabled");
        }
        WebSearchInput normalized = normalize(input);
        policy.validate(normalized.query());
        WebSearchResult result = provider.search(normalized);
        if (result == null || ((result.summary() == null || result.summary().isBlank())
                && result.sources().isEmpty())) {
            throw new AgentException("AGENT_WEB_SEARCH_NO_RESULTS", HttpStatus.NOT_FOUND,
                    "No reliable public web search results were found");
        }
        return result;
    }

    private WebSearchInput normalize(WebSearchInput input) {
        if (input == null || input.query() == null) return invalidInput();
        String query = input.query().trim().replaceAll("\\s+", " ");
        if (query.isEmpty() || query.length() > 300) return invalidInput();
        if (input.maxResults() != null && input.maxResults() < 1) return invalidInput();
        int maxResults = input.maxResults() == null ? properties.maxResults() : Math.min(input.maxResults(), 10);
        String freshness = input.freshness();
        if (freshness != null) {
            freshness = freshness.trim().toLowerCase(Locale.ROOT);
            if (!FRESHNESS_VALUES.contains(freshness)) return invalidInput();
        }
        return new WebSearchInput(query, maxResults, freshness);
    }

    private static WebSearchInput invalidInput() {
        throw new AgentException("AGENT_WEB_SEARCH_INVALID_INPUT", HttpStatus.BAD_REQUEST,
                "Invalid web search input");
    }
}
