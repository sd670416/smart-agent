package com.smart.agent.tool.web;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.ModelGatewayProperties;
import com.smart.agent.tool.ToolContext;
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
        return search(input, null);
    }

    @Override
    public WebSearchResult search(WebSearchInput input, ToolContext context) {
        return switch (resolveProvider(context)) {
            case "openai" -> openAi.search(input, context);
            case "zhipu" -> zhipu.search(input, context);
            default -> throw unsupported();
        };
    }

    /**
     * 解析本轮使用的联网提供方。
     *
     * <p>显式配置的提供方优先级最高；自动模式优先依据本轮会话所选模型的能力与地址判断，
     * 只有未绑定模型时才回退到全局 YAML 配置。
     */
    private String resolveProvider(ToolContext context) {
        String configured = webSearch.provider().toLowerCase(Locale.ROOT);
        if (!"auto".equals(configured)) return configured;
        ToolContext.ModelBinding binding = context == null ? null : context.modelBinding();
        if (binding != null) {
            return resolveFromBinding(binding);
        }
        return resolveFromProperties();
    }

    private String resolveFromBinding(ToolContext.ModelBinding binding) {
        if (!binding.supportsWebSearch()) {
            throw unsupported();
        }
        return matchByEndpointOrName(binding.baseUrl(), binding.modelName());
    }

    private String resolveFromProperties() {
        return matchByEndpointOrName(model.baseUrl(), model.chatModel());
    }

    private String matchByEndpointOrName(String rawBaseUrl, String rawModelName) {
        String baseUrl = rawBaseUrl == null ? "" : rawBaseUrl.toLowerCase(Locale.ROOT);
        String modelName = rawModelName == null ? "" : rawModelName.toLowerCase(Locale.ROOT);
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
