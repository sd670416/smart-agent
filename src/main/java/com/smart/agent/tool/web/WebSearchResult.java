package com.smart.agent.tool.web;

import java.util.List;

public record WebSearchResult(
        String query,
        String searchedAt,
        String summary,
        List<WebSearchSource> sources,
        String provider) {
    public WebSearchResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
