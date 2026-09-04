package com.smart.agent.knowledge;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QdrantProperties.class)
public class KnowledgeConfiguration {

    @Bean
    @Profile({"local", "test"})
    @ConditionalOnProperty(prefix = "agent.embedding", name = "mode", havingValue = "local-hash")
    @ConditionalOnMissingBean(EmbeddingGateway.class)
    EmbeddingGateway localHashEmbeddingGateway() {
        return new LocalHashEmbeddingGateway();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agent.qdrant", name = "enabled", havingValue = "true")
    QdrantClient qdrantClient(QdrantProperties properties) {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                properties.getHost(), properties.getGrpcPort(), properties.isTls());
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.withApiKey(properties.getApiKey());
        }
        return new QdrantClient(builder.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.qdrant", name = "enabled", havingValue = "true")
    VectorIndex qdrantVectorIndex(QdrantClient client, QdrantProperties properties) {
        return new QdrantVectorIndex(client, properties.getCollectionName(),
                properties.getAttachmentCollectionName(), properties.getVectorDimension());
    }
}
