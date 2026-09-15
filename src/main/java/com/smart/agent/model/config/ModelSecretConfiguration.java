package com.smart.agent.model.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型 API Key 存储装配。
 *
 * <p>当前采用无主密钥的明文持久化策略，部署时不创建或注入任何加密密钥。
 * 对外接口和日志仍由各自边界负责脱敏。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ModelSecretConfiguration {

    @Bean
    ModelSecretCipher modelSecretCipher() {
        return new PlainTextModelSecretCipher();
    }
}
