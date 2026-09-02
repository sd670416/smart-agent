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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@Tag("qdrant-external")
@EnabledIfSystemProperty(named = "agent.it.qdrant.external", matches = "true")
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "agent.persistence.enabled=false",
        "agent.qdrant.enabled=false",
        "AGENT_LOCAL_CONTEXT_SECRET=test-only-context-secret"
})
class ExternalQdrantVectorIndexIT {

    @Autowired
    private Environment environment;

    private QdrantClient client;
    private QdrantVectorIndex index;
    private String collectionName;

    @BeforeEach
    void setUp() {
        String host = environment.getRequiredProperty("agent.qdrant.host");
        int port = environment.getRequiredProperty("agent.qdrant.grpc-port", Integer.class);
        boolean tls = environment.getProperty("agent.qdrant.tls", Boolean.class, false);
        String apiKey = environment.getProperty("agent.qdrant.api-key", "");
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, port, tls);
        if (!apiKey.isBlank()) {
            builder.withApiKey(apiKey);
        }
        client = new QdrantClient(builder.build());
        collectionName = "agent_knowledge_external_it_" + UUID.randomUUID().toString().replace("-", "");
        index = new QdrantVectorIndex(client, collectionName, 2);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client == null) {
            return;
        }
        try {
            if (collectionName != null && client.collectionExistsAsync(collectionName).get()) {
                client.deleteCollectionAsync(collectionName).get();
            }
        } finally {
            client.close();
        }
    }

    @Test
    void enforcesIsolationScopesTopKDeletionAndIdempotentReindex() {
        index.upsert(List.of(
                chunk("allowed", "doc-a", "tenant-1", "space-1", "project-x", "published", "first", 1, 0),
                chunk("project", "doc-b", "tenant-1", "space-x", "project-1", "published", "project", 0.9f, 0.1f),
                chunk("denied", "doc-c", "tenant-1", "space-x", "project-x", "published", "denied", 0.8f, 0.2f),
                chunk("other-tenant", "doc-d", "tenant-2", "space-1", "project-1", "published", "other", 1, 0),
                chunk("draft", "doc-e", "tenant-1", "space-1", "project-1", "draft", "draft", 1, 0)));

        List<VectorHit> scoped = index.search(query(Set.of("space-1"), Set.of("project-1"), 1));
        assertThat(scoped).extracting(VectorHit::chunkId).containsExactly("allowed");

        index.upsert(List.of(chunk(
                "allowed", "doc-a", "tenant-1", "space-1", "project-x", "published", "updated", 1, 0)));
        assertThat(index.search(query(Set.of("space-1"), Set.of(), 10)))
                .singleElement().satisfies(hit -> assertThat(hit.content()).isEqualTo("updated"));

        index.deleteDocument("tenant-1", "doc-a");
        index.deleteDocument("tenant-1", "doc-a");
        assertThat(index.search(query(Set.of("space-1"), Set.of(), 10))).isEmpty();
        assertThat(index.search(new VectorSearchQuery(
                "tenant-2", Set.of("space-1"), Set.of(), List.of(1.0f, 0.0f), 10)))
                .extracting(VectorHit::chunkId).containsExactly("other-tenant");
    }

    private static IndexedChunk chunk(String chunkId, String documentId, String tenantId, String spaceId,
            String projectId, String status, String content, float first, float second) {
        return new IndexedChunk(
                chunkId, documentId, tenantId, spaceId, projectId, status, content, List.of(first, second));
    }

    private static VectorSearchQuery query(Set<String> spaces, Set<String> projects, int topK) {
        return new VectorSearchQuery("tenant-1", spaces, projects, List.of(1.0f, 0.0f), topK);
    }
}
