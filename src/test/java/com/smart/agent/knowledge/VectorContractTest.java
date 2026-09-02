package com.smart.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qdrant.client.grpc.Common.Filter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VectorContractTest {

    @Test
    void queryRequiresTenantVectorTopKAndAtLeastOneTrustedScope() {
        assertThatThrownBy(() -> new VectorSearchQuery(" ", Set.of("space-1"), Set.of(), vector(), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VectorSearchQuery("tenant-1", Set.of(), Set.of(), vector(), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        assertThatThrownBy(() -> new VectorSearchQuery("tenant-1", Set.of("space-1"), Set.of(), List.of(), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VectorSearchQuery("tenant-1", Set.of("space-1"), Set.of(), vector(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VectorSearchQuery("tenant-1", Set.of("space-1"), Set.of(), vector(), 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filterAlwaysRequiresTenantPublishedStatusAndOneAllowedScope() {
        VectorSearchQuery query = new VectorSearchQuery(
                "tenant-1", Set.of("space-2", "space-1"), Set.of("project-1"), vector(), 5);

        Filter filter = QdrantVectorIndex.buildFilter(query);

        assertThat(filter.getMustList()).extracting(condition -> condition.getField().getKey())
                .containsExactly("tenant_id", "status");
        assertThat(filter.getMust(0).getField().getMatch().getKeyword()).isEqualTo("tenant-1");
        assertThat(filter.getMust(1).getField().getMatch().getKeyword()).isEqualTo("published");
        assertThat(filter.getMinShould().getMinCount()).isEqualTo(1);
        assertThat(filter.getMinShould().getConditionsList())
                .extracting(condition -> condition.getField().getKey())
                .containsExactly("space_id", "project_id");
        assertThat(filter.getMinShould().getConditions(0).getField().getMatch().getKeywords().getStringsList())
                .containsExactly("space-1", "space-2");
        assertThat(filter.getMinShould().getConditions(1).getField().getMatch().getKeywords().getStringsList())
                .containsExactly("project-1");
    }

    @Test
    void indexedChunkRejectsBlankMetadataAndNonFiniteVectors() {
        assertThatThrownBy(() -> new IndexedChunk(
                "chunk-1", "doc-1", "tenant-1", "space-1", null, "published", "text",
                List.of(Float.NaN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IndexedChunk(
                "chunk-1", "doc-1", "tenant-1", "space-1", null, "published", " ", vector()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Float> vector() {
        return List.of(1.0f, 0.0f);
    }
}
