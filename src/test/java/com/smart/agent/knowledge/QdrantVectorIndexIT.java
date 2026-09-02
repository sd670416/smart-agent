package com.smart.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("qdrant")
@EnabledIfSystemProperty(named = "agent.it.qdrant", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
class QdrantVectorIndexIT {

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>("qdrant/qdrant:v1.18.2")
            .withExposedPorts(6334);

    private QdrantClient client;
    private QdrantVectorIndex index;
    private String collectionName;

    @BeforeEach
    void setUp() {
        collectionName = "agent_knowledge_it_" + UUID.randomUUID().toString().replace("-", "");
        client = new QdrantClient(QdrantGrpcClient.newBuilder(
                QDRANT.getHost(), QDRANT.getMappedPort(6334), false).build());
        index = new QdrantVectorIndex(client, collectionName, 2);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client.collectionExistsAsync(collectionName).get()) {
                client.deleteCollectionAsync(collectionName).get();
            }
        } finally {
            client.close();
        }
    }

    @Test
    void searchNeverReturnsAnotherTenantOrUnpublishedChunk() {
        index.upsert(List.of(
                chunk("a", "doc-a", "tenant-1", "space-1", "project-1", "published", vector(1, 0)),
                chunk("b", "doc-b", "tenant-2", "space-1", "project-1", "published", vector(1, 0)),
                chunk("c", "doc-c", "tenant-1", "space-1", "project-1", "draft", vector(1, 0))));

        List<VectorHit> hits = index.search(query(
                "tenant-1", Set.of("space-1"), Set.of("project-1"), vector(1, 0), 10));

        assertThat(hits).extracting(VectorHit::chunkId).containsExactly("a");
    }

    @Test
    void searchRequiresAnAllowedSpaceOrProject() {
        index.upsert(List.of(
                chunk("space-allowed", "doc-a", "tenant-1", "space-1", "project-x", "published", vector(1, 0)),
                chunk("project-allowed", "doc-b", "tenant-1", "space-x", "project-1", "published", vector(1, 0)),
                chunk("denied", "doc-c", "tenant-1", "space-x", "project-x", "published", vector(1, 0))));

        assertThat(index.search(query("tenant-1", Set.of("space-1"), Set.of(), vector(1, 0), 10)))
                .extracting(VectorHit::chunkId)
                .containsExactly("space-allowed");
        assertThat(index.search(query("tenant-1", Set.of(), Set.of("project-1"), vector(1, 0), 10)))
                .extracting(VectorHit::chunkId)
                .containsExactly("project-allowed");
        assertThat(index.search(query(
                "tenant-1", Set.of("space-1"), Set.of("project-1"), vector(1, 0), 10)))
                .extracting(VectorHit::chunkId)
                .containsExactlyInAnyOrder("space-allowed", "project-allowed");
    }

    @Test
    void searchHonorsTopK() {
        index.upsert(List.of(
                chunk("best", "doc-a", "tenant-1", "space-1", null, "published", vector(1, 0)),
                chunk("second", "doc-b", "tenant-1", "space-1", null, "published", vector(0.8f, 0.2f)),
                chunk("third", "doc-c", "tenant-1", "space-1", null, "published", vector(0, 1))));

        assertThat(index.search(query("tenant-1", Set.of("space-1"), Set.of(), vector(1, 0), 2)))
                .extracting(VectorHit::chunkId)
                .containsExactly("best", "second");
    }

    @Test
    void deleteDocumentIsTenantScopedAndIdempotent() {
        index.upsert(List.of(
                chunk("tenant-1-chunk", "shared-doc", "tenant-1", "space-1", null, "published", vector(1, 0)),
                chunk("tenant-2-chunk", "shared-doc", "tenant-2", "space-1", null, "published", vector(1, 0))));

        index.deleteDocument("tenant-1", "shared-doc");
        index.deleteDocument("tenant-1", "shared-doc");

        assertThat(index.search(query("tenant-1", Set.of("space-1"), Set.of(), vector(1, 0), 10))).isEmpty();
        assertThat(index.search(query("tenant-2", Set.of("space-1"), Set.of(), vector(1, 0), 10)))
                .extracting(VectorHit::chunkId)
                .containsExactly("tenant-2-chunk");
    }

    @Test
    void reindexingSameChunkUpdatesOneDeterministicPoint() {
        IndexedChunk first = chunk(
                "stable-chunk", "doc-a", "tenant-1", "space-1", null, "published", vector(1, 0));
        IndexedChunk updated = new IndexedChunk(
                first.chunkId(), first.documentId(), first.tenantId(), first.spaceId(), first.projectId(),
                first.status(), "updated content", vector(1, 0));

        index.upsert(List.of(first));
        index.upsert(List.of(updated));

        List<VectorHit> hits = index.search(query(
                "tenant-1", Set.of("space-1"), Set.of(), vector(1, 0), 10));
        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.chunkId()).isEqualTo("stable-chunk");
            assertThat(hit.content()).isEqualTo("updated content");
        });
    }

    private static IndexedChunk chunk(String chunkId, String documentId, String tenantId, String spaceId,
            String projectId, String status, List<Float> vector) {
        return new IndexedChunk(chunkId, documentId, tenantId, spaceId, projectId, status,
                "content-" + chunkId, vector);
    }

    private static VectorSearchQuery query(String tenantId, Set<String> spaceIds, Set<String> projectIds,
            List<Float> vector, int topK) {
        return new VectorSearchQuery(tenantId, spaceIds, projectIds, vector, topK);
    }

    private static List<Float> vector(float first, float second) {
        return List.of(first, second);
    }
}
