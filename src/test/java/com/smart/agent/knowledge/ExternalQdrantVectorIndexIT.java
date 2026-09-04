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
    private String knowledgeCollectionName;
    private String attachmentCollectionName;

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
        String suffix = UUID.randomUUID().toString().replace("-", "");
        knowledgeCollectionName = "agent_knowledge_external_it_" + suffix;
        attachmentCollectionName = "agent_attachment_external_it_" + suffix;
        index = new QdrantVectorIndex(client, knowledgeCollectionName, attachmentCollectionName, 2);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client == null) {
            return;
        }
        try {
            deleteCollectionIfPresent(knowledgeCollectionName);
            deleteCollectionIfPresent(attachmentCollectionName);
        } finally {
            client.close();
        }
    }

    private void deleteCollectionIfPresent(String collectionName) throws Exception {
        if (collectionName != null && client.collectionExistsAsync(collectionName).get()) {
            client.deleteCollectionAsync(collectionName).get();
        }
    }

    @Test
    void enforcesIsolationScopesTopKDeletionAndIdempotentReindex() {
        index.upsert(List.of(
                chunk("allowed", "doc-a", "tenant-1", "space-1", "project-1", "published", "first", 1, 0),
                chunk("project", "doc-b", "tenant-1", "space-x", "project-1", "published", "project", 0.9f, 0.1f),
                chunk("denied", "doc-c", "tenant-1", "space-x", "project-x", "published", "denied", 0.8f, 0.2f),
                chunk("other-tenant", "doc-d", "tenant-2", "space-1", "project-1", "published", "other", 1, 0),
                chunk("draft", "doc-e", "tenant-1", "space-1", "project-1", "draft", "draft", 1, 0)));

        List<VectorHit> scoped = index.search(query(Set.of("space-1"), Set.of("project-1"), 1));
        assertThat(scoped).extracting(VectorHit::chunkId).containsExactly("allowed");

        index.upsert(List.of(chunk(
                "allowed", "doc-a", "tenant-1", "space-1", "project-1", "published", "updated", 1, 0)));
        assertThat(index.search(query(Set.of("space-1"), Set.of(), 10)))
                .singleElement().satisfies(hit -> assertThat(hit.content()).isEqualTo("updated"));

        index.deleteDocument("tenant-1", "doc-a");
        index.deleteDocument("tenant-1", "doc-a");
        assertThat(index.search(query(Set.of("space-1"), Set.of(), 10))).isEmpty();
        assertThat(index.search(new VectorSearchQuery(
                "tenant-2", Set.of("space-1"), Set.of(), List.of(1.0f, 0.0f), 10)))
                .extracting(VectorHit::chunkId).containsExactly("other-tenant");
    }

    @Test
    void isolatesKnowledgeAndTemporaryAttachmentCollections() {
        IndexedChunk knowledge = chunk(
                "knowledge", "doc-shared", "tenant-1", "space-1", "project-1",
                "published", "knowledge content", 1, 0);
        IndexedChunk attachment = new IndexedChunk(
                "attachment", "doc-shared", "version-1", "tenant-1", "space-1", "project-1",
                "published", "attachment-1", java.time.Instant.parse("2026-09-06T00:00:00Z"),
                null, null, null, "attachment content", List.of(1.0f, 0.0f));

        index.upsert(VectorNamespace.KNOWLEDGE, List.of(knowledge));
        index.upsert(VectorNamespace.CHAT_ATTACHMENT, List.of(attachment));

        assertThat(index.search(VectorNamespace.KNOWLEDGE, query(Set.of("space-1"), Set.of("project-1"), 10)))
                .extracting(VectorHit::chunkId).containsExactly("knowledge");
        assertThat(index.search(VectorNamespace.CHAT_ATTACHMENT,
                query(Set.of("space-1"), Set.of("project-1"), 10)))
                .extracting(VectorHit::chunkId).containsExactly("attachment");

        index.deleteAttachment(VectorNamespace.CHAT_ATTACHMENT, "tenant-1", "attachment-1");

        assertThat(index.search(VectorNamespace.CHAT_ATTACHMENT,
                query(Set.of("space-1"), Set.of("project-1"), 10))).isEmpty();
        assertThat(index.search(VectorNamespace.KNOWLEDGE, query(Set.of("space-1"), Set.of("project-1"), 10)))
                .extracting(VectorHit::chunkId).containsExactly("knowledge");
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
