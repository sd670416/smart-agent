package com.smart.agent.tool.web;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelGatewayProperties;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class WebSearchProviderRouter implements WebSearchProvider {
    private final WebSearchProperties webSearch;
    private final ModelGatewayProperties model;
    private final WebSearchProvider openAi;
    private final WebSearchProvider zhipu;

    public WebSearchProviderRouter(
            WebSearchProperties webSearch,
            ModelGatewayProperties model,
            WebSearchProvider openAi,
            WebSearchProvider zhipu) {
        this.webSearch = webSearch;
        this.model = model;
        this.openAi = openAi;
        this.zhipu = zhipu;
    }

    @Override
    public WebSearchResult search(WebSearchInput input) {
        return switch (resolveProvider()) {
            case "openai" -> openAi.search(input);
            case "zhipu" -> zhipu.search(input);
            default -> throw unsupported();
        };
    }

    private String resolveProvider() {
        String configured = webSearch.provider().toLowerCase(Locale.ROOT);
        if (!"auto".equals(configured)) return configured;
        String baseUrl = model.baseUrl().toLowerCase(Locale.ROOT);
        String modelName = model.chatModel().toLowerCase(Locale.ROOT);
        if (baseUrl.contains("bigmodel.cn") || modelName.startsWith("glm-")) return "zhipu";
        if (baseUrl.contains("openai.com") || modelName.startsWith("gpt-")
                || modelName.matches("o[134]-.*")) return "openai";
        return "unsupported";
    }

    private static AgentException unsupported() {
        return new AgentException("AGENT_WEB_SEARCH_PROVIDER_UNSUPPORTED", HttpStatus.BAD_REQUEST,
                "Configured model provider does not support web search");
    }
}
