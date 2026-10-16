package com.smart.agent.tool.income;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class OtherIncomeRegistrationBusinessClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "smart-boot", matchIfMissing = true)
    OtherIncomeRegistrationBusinessClient smartBootOtherIncomeRegistrationBusinessClient(ObjectMapper mapper,
            @Value("${agent.business.base-url:http://localhost:8888}") String baseUrl,
            @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret) {
        return new SmartBootOtherIncomeRegistrationBusinessClient(WebClient.builder().baseUrl(baseUrl).build(), mapper, secret);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "local")
    OtherIncomeRegistrationBusinessClient localOtherIncomeRegistrationBusinessClient() {
        return (context, input) -> { throw new com.smart.agent.common.error.AgentException("AGENT_QUERY_UNAVAILABLE",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "其他收入登记查询需要配置 smart-boot 业务接口"); };
    }
}
