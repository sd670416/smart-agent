package com.smart.agent.tool.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.model.ModelGatewayProperties;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WebSearchProperties.class, ModelGatewayProperties.class})
public class WebSearchClientConfiguration {
    @Bean
    WebSearchPolicy webSearchPolicy() {
        return new WebSearchPolicy();
    }

    @Bean
    OpenAiWebSearchProvider openAiWebSearchProvider(
            ObjectMapper objectMapper,
            ModelGatewayProperties modelProperties,
            WebSearchProperties webSearchProperties,
            @Value("${agent.time-zone:Asia/Shanghai}") String timezone) {
        return new OpenAiWebSearchProvider(
                WebClient.builder().build(), objectMapper, modelProperties, webSearchProperties.timeout(),
                Clock.systemUTC(), ZoneId.of(timezone));
    }

    @Bean
    ZhipuWebSearchProvider zhipuWebSearchProvider(
            ObjectMapper objectMapper,
            ModelGatewayProperties modelProperties,
            WebSearchProperties webSearchProperties,
            @Value("${agent.time-zone:Asia/Shanghai}") String timezone) {
        return new ZhipuWebSearchProvider(
                WebClient.builder().build(), objectMapper, modelProperties, webSearchProperties.timeout(),
                Clock.systemUTC(), ZoneId.of(timezone));
    }

    @Bean
    WebSearchProviderRouter webSearchProviderRouter(
            WebSearchProperties webSearchProperties,
            ModelGatewayProperties modelProperties,
            OpenAiWebSearchProvider openAi,
            ZhipuWebSearchProvider zhipu) {
        return new WebSearchProviderRouter(webSearchProperties, modelProperties, openAi, zhipu);
    }

    @Bean
    WebSearchTool webSearchTool(
            WebSearchProperties properties, WebSearchPolicy policy, WebSearchProviderRouter provider) {
        return new WebSearchTool(properties, policy, provider);
    }
}
