package com.smart.agent.model;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agent.model")
public record ModelGatewayProperties(
        String mode,
        String baseUrl,
        String apiKey,
        String chatModel,
        Duration connectTimeout,
        Duration readTimeout) {

    public ModelGatewayProperties {
        mode = defaultValue(mode, "local");
        baseUrl = defaultValue(baseUrl, "http://localhost:11434/v1");
        apiKey = defaultValue(apiKey, "local-development-key");
        chatModel = defaultValue(chatModel, "local-test-model");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
