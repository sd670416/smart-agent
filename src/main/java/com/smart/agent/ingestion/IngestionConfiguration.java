package com.smart.agent.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class IngestionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DocumentParserRegistry documentParserRegistry() {
        return DocumentParserRegistry.defaults();
    }

    @Bean
    @ConditionalOnMissingBean
    ParseLimits documentParseLimits() {
        return ParseLimits.defaults();
    }
}
