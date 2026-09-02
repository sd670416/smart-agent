package com.smart.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalHashEmbeddingGatewayTest {

    private final LocalHashEmbeddingGateway gateway = new LocalHashEmbeddingGateway();

    @Test
    void createsDeterministicNormalized64DimensionalEmbedding() {
        List<Float> first = gateway.embed("项目安全检查");
        List<Float> second = gateway.embed("项目安全检查");

        double norm = Math.sqrt(first.stream().mapToDouble(value -> value * value).sum());
        assertThat(first).hasSize(64).containsExactlyElementsOf(second);
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(gateway.modelKey()).isEqualTo("local-hash-v1");
    }

    @Test
    void differentTextProducesDifferentEmbedding() {
        assertThat(gateway.embed("安全")).isNotEqualTo(gateway.embed("质量"));
    }
}
