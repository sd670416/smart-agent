package com.smart.agent.tool.web;

@FunctionalInterface
public interface WebSearchProvider {
    WebSearchResult search(WebSearchInput input);
}
