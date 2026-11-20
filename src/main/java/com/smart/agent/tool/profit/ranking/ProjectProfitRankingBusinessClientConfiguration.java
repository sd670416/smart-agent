package com.smart.agent.tool.profit.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class ProjectProfitRankingBusinessClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "smart-boot", matchIfMissing = true)
    ProjectProfitRankingBusinessClient smartBootProjectProfitRankingBusinessClient(ObjectMapper mapper,
            @Value("${agent.business.base-url:http://localhost:8888}") String baseUrl,
            @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret) {
        return new SmartBootProjectProfitRankingBusinessClient(WebClient.builder().baseUrl(baseUrl).build(), mapper, secret);
    }
    @Bean
    @ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "local")
    ProjectProfitRankingBusinessClient localProjectProfitRankingBusinessClient() {
        return (context, input) -> { throw new AgentException("AGENT_QUERY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "利润排名需要连接业务服务，请确认服务配置"); };
    }
}
