package com.smart.agent.tool.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class ProjectOutputBusinessClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "smart-boot", matchIfMissing = true)
    ProjectOutputBusinessClient smartBootProjectOutputBusinessClient(ObjectMapper mapper,
            @Value("${agent.business.base-url:http://localhost:8888}") String baseUrl,
            @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret) {
        return new SmartBootProjectOutputBusinessClient(WebClient.builder().baseUrl(baseUrl).build(), mapper, secret);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "local")
    ProjectOutputBusinessClient localProjectOutputBusinessClient() {
        return (context, input) -> { throw new com.smart.agent.common.error.AgentException("AGENT_QUERY_UNAVAILABLE",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "工程产值查询需要配置 smart-boot 业务接口"); };
    }
}

