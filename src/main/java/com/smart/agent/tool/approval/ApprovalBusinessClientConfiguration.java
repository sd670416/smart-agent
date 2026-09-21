package com.smart.agent.tool.approval;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class ApprovalBusinessClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode",
            havingValue = "smart-boot", matchIfMissing = true)
    public ApprovalBusinessClient smartBootApprovalBusinessClient(
            ObjectMapper objectMapper,
            @Value("${agent.business.base-url:http://localhost:8888}") String baseUrl,
            @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String internalSecret) {
        return new SmartBootApprovalBusinessClient(
                WebClient.builder().baseUrl(baseUrl).build(), objectMapper, internalSecret);
    }
}
