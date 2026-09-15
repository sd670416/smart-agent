package com.smart.agent.model.config;

import java.time.Duration;

interface ModelTestTransport {

    Response post(String url, ModelDeploymentType deploymentType, String apiKey, String jsonBody,
            Duration connectTimeout, Duration readTimeout);

    record Response(int statusCode, String body, long durationMillis) {
    }
}
