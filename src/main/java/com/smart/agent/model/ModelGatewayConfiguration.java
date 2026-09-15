package com.smart.agent.model;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smart.agent.model.audit.ModelCallAuditService;
import com.smart.agent.model.config.ModelBootstrapProperties;
import org.springframework.beans.factory.ObjectProvider;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ModelGatewayProperties.class, ModelBootstrapProperties.class})
public class ModelGatewayConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ModelGatewayConfiguration.class);

    @Bean
    ModelGatewayFactory modelGatewayFactory(ObjectProvider<ModelCallAuditService> audit) {
        return (config, apiKey) -> createGateway(config.baseUrl(), apiKey, config.modelName(),
                config.connectTimeout(), config.readTimeout(), audit.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.model", name = "mode", havingValue = "local", matchIfMissing = true)
    ModelGateway localDeterministicModelGateway() {
        return new LocalDeterministicModelGateway();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.model", name = "mode", havingValue = "openai-compatible")
    ModelGateway openAiCompatibleModelGateway(ModelGatewayProperties properties,
            ObjectProvider<ModelCallAuditService> audit) {
        log.info("AI模型配置: mode={}, baseUrl={}, chatModel={}, connectTimeout={}, readTimeout={}, apiKeyPresent={}",
                properties.mode(), properties.baseUrl(), properties.chatModel(), properties.connectTimeout(),
                properties.readTimeout(), properties.apiKey() != null && !properties.apiKey().isBlank());
        return createGateway(properties.baseUrl(), properties.apiKey(), properties.chatModel(),
                properties.connectTimeout(), properties.readTimeout(), audit.getIfAvailable()).gateway();
    }

    private static ModelGatewayFactory.CreatedGateway createGateway(String baseUrl, String apiKey, String modelName,
            java.time.Duration connectTimeout, java.time.Duration readTimeout, ModelCallAuditService audit) {
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey == null || apiKey.isBlank() ? "local-no-api-key" : apiKey)
                .modelName(modelName)
                .httpClientBuilder(new JdkHttpClientBuilder()
                        .connectTimeout(connectTimeout)
                        .readTimeout(readTimeout))
                .logRequests(false)
                .logResponses(false)
                .build();
        ModelGateway gateway = new OpenAiCompatibleModelGateway(model, readTimeout, audit, modelName, baseUrl);
        Runnable close = model instanceof AutoCloseable closeable ? () -> closeQuietly(closeable) : () -> { };
        return new ModelGatewayFactory.CreatedGateway(gateway, close);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception failure) {
            log.warn("关闭模型客户端失败", failure);
        }
    }
}
