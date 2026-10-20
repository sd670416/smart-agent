package com.smart.agent.tool.company;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
@Configuration(proxyBeanMethods=false)
public class CompanyOperatingBusinessClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix="agent.business",name="mode",havingValue="smart-boot",matchIfMissing=true)
    CompanyOperatingBusinessClient smartBootCompanyOperatingClient(ObjectMapper mapper,@Value("${agent.business.base-url:http://localhost:8888}")String url,@Value("${AGENT_LOCAL_CONTEXT_SECRET}")String secret) {
        return new SmartBootCompanyOperatingBusinessClient(WebClient.builder().baseUrl(url).build(),mapper,secret);
    }
    @Bean
    @ConditionalOnProperty(prefix="agent.business",name="mode",havingValue="local")
    CompanyOperatingBusinessClient localCompanyOperatingClient() {
        return (context,input)->{throw new com.smart.agent.common.error.AgentException("AGENT_QUERY_UNAVAILABLE",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"公司经营查询需要配置业务接口");};
    }
}
