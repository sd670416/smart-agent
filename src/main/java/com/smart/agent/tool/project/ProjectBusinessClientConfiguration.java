package com.smart.agent.tool.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class ProjectBusinessClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode",
            havingValue = "smart-boot", matchIfMissing = true)
    ProjectBusinessClient smartBootProjectBusinessClient(
            ObjectMapper objectMapper,
            @Value("${agent.business.base-url:http://localhost:8888}") String baseUrl,
            @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String internalSecret) {
        WebClient client = WebClient.builder().baseUrl(baseUrl).build();
        return new SmartBootProjectBusinessClient(client, objectMapper, internalSecret);
    }
}
