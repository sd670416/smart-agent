package com.smart.agent.tool.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agent.web-search")
public record WebSearchProperties(boolean enabled, String provider, int maxResults, Duration timeout) {
    public WebSearchProperties {
        provider = provider == null || provider.isBlank() ? "auto" : provider.trim();
        maxResults = maxResults <= 0 ? 5 : Math.min(maxResults, 10);
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
    }
}
