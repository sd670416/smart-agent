package com.smart.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KnowledgeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KnowledgeConfiguration.class)
            .withPropertyValues("agent.embedding.mode=local-hash", "agent.qdrant.enabled=false");

    @Test
    void localHashEmbeddingIsAvailableOnlyInLocalOrTestProfiles() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).hasSingleBean(EmbeddingGateway.class));
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).doesNotHaveBean(EmbeddingGateway.class));
    }
}
