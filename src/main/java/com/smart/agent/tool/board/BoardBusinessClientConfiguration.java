package com.smart.agent.tool.board;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
@Configuration(proxyBeanMethods=false)
public class BoardBusinessClientConfiguration {
 @Bean @ConditionalOnProperty(prefix="agent.business",name="mode",havingValue="smart-boot",matchIfMissing=true)
 BoardBusinessClient smartBootBoardBusinessClient(ObjectMapper mapper,@Value("${agent.business.base-url:http://localhost:8888}") String base,@Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret){return new SmartBootBoardBusinessClient(WebClient.builder().baseUrl(base).build(),mapper,secret);}
}
