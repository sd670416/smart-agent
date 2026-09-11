package com.smart.agent.tool.system;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeToolConfiguration {
    @Bean
    CurrentTimeTool currentTimeTool(@Value("${agent.time-zone:Asia/Shanghai}") String timezone) {
        return new CurrentTimeTool(Clock.systemUTC(), ZoneId.of(timezone));
    }
}
