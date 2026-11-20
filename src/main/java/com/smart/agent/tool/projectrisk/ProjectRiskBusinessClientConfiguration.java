package com.smart.agent.tool.projectrisk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
@Configuration(proxyBeanMethods=false)
public class ProjectRiskBusinessClientConfiguration {
    @Bean @ConditionalOnProperty(prefix="agent.business",name="mode",havingValue="smart-boot",matchIfMissing=true)
    ProjectRiskBusinessClient smartBootProjectRiskBusinessClient(ObjectMapper mapper,@Value("${agent.business.base-url:http://localhost:8888}")String url,@Value("${AGENT_LOCAL_CONTEXT_SECRET}")String secret){return new SmartBootProjectRiskBusinessClient(WebClient.builder().baseUrl(url).build(),mapper,secret);}
    @Bean @ConditionalOnProperty(prefix="agent.business",name="mode",havingValue="local")
    ProjectRiskBusinessClient localProjectRiskBusinessClient(){return (c,i)->{throw new AgentException("AGENT_QUERY_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE,"风险与工期查询需要连接业务服务");};}
}
