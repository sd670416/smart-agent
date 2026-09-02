package com.smart.agent.model;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModelGatewayProperties.class)
public class ModelGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "agent.model", name = "mode", havingValue = "local", matchIfMissing = true)
    ModelGateway localDeterministicModelGateway() {
        return new LocalDeterministicModelGateway();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.model", name = "mode", havingValue = "openai-compatible")
    ModelGateway openAiCompatibleModelGateway(ModelGatewayProperties properties) {
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .modelName(properties.chatModel())
                .httpClientBuilder(new JdkHttpClientBuilder()
                        .connectTimeout(properties.connectTimeout())
                        .readTimeout(properties.readTimeout()))
                .logRequests(false)
                .logResponses(false)
                .build();
        return new OpenAiCompatibleModelGateway(model, properties.readTimeout());
    }
}
