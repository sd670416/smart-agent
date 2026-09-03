package com.smart.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.security.AgentUserContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeSearchServiceTest {

    private final RecordingRepository repository = new RecordingRepository();
    private final RecordingVectorIndex index = new RecordingVectorIndex();
    private final RecordingEmbeddingGateway embeddingGateway = new RecordingEmbeddingGateway();
    private final KnowledgeSearchService service = new KnowledgeSearchService(
            repository, embeddingGateway, index);

    @Test
    void returnsOnlyAllowedPublishedEvidenceWithStableCitationLocation() {
        repository.add(publishedChunk("chunk-1", "doc-1", "version-1", "tenant-1", "space-1", "project-1",
                "Safety guide", 3, "Safety inspection", "Authoritative safety requirements"));
        index.hits = List.of(hit("chunk-1", "doc-1", 0.91, "Untrusted vector content", "tenant-1", "space-1", "project-1"));

        List<KnowledgeCitation> result = service.search(
                new KnowledgeSearchQuery("Safety inspection requirements", Set.of("space-1"), "project-1", 5),
                context("tenant-1", Set.of("project-1")));

        assertThat(result).singleElement().satisfies(citation -> {
            assertThat(citation.documentId()).isEqualTo("doc-1");
            assertThat(citation.versionId()).isEqualTo("version-1");
            assertThat(citation.title()).isEqualTo("Safety guide");
            assertThat(citation.location()).isEqualTo("第3页 / Safety inspection");
            assertThat(citation.excerpt()).isEqualTo("Authoritative safety requirements");
            assertThat(citation.score()).isEqualTo(0.91);
            assertThat(citation.citationToken()).matches("[0-9a-f]{64}");
        });
        assertThat(index.lastQuery).isEqualTo(new VectorSearchQuery(
                "tenant-1", Set.of("space-1"), Set.of("project-1", IndexedChunk.GLOBAL_PROJECT_ID),
                List.of(0.25f, 0.75f), 5));
    }

    @Test
    void discardsTenantMismatchedAndStaleVectorHits() {
        repository.add(publishedChunk("chunk-1", "doc-1", "version-1", "tenant-1", "space-1", "project-1",
                "Safety guide", null, null, "Allowed evidence"));
        repository.add(publishedChunk("chunk-project-2", "doc-project-2", "version-project-2", "tenant-1", "space-1", "project-2",
                "Other project guide", null, null, "Other project evidence"));
        index.hits = List.of(
                hit("chunk-tenant-2", "doc-tenant-2", 0.99, "Other tenant", "tenant-2", "space-1", "project-1"),
                hit("stale-chunk", "stale-doc", 0.95, "Stale", "tenant-1", "space-1", "project-1"),
                hit("chunk-project-2", "doc-project-2", 0.93, "Other project", "tenant-1", "space-1", "project-2"),
                hit("chunk-1", "doc-1", 0.91, "Allowed", "tenant-1", "space-1", "project-1"));

        List<KnowledgeCitation> result = service.search(query(5), context("tenant-1", Set.of("project-1")));

        assertThat(result).extracting(KnowledgeCitation::documentId).containsExactly("doc-1");
    }

    @Test
    void discardsDeletedAndUnpublishedAuthoritativeRows() {
        repository.add(unpublishedChunk("chunk-draft", "doc-draft", "version-draft", "tenant-1", "space-1", "project-1"));
        repository.add(deletedChunk("chunk-deleted", "doc-deleted", "version-deleted", "tenant-1", "space-1", "project-1"));
        index.hits = List.of(
                hit("chunk-draft", "doc-draft", 0.99, "Draft", "tenant-1", "space-1", "project-1"),
                hit("chunk-deleted", "doc-deleted", 0.98, "Deleted", "tenant-1", "space-1", "project-1"));

        assertThat(service.search(query(5), context("tenant-1", Set.of("project-1")))).isEmpty();
    }

    @Test
    void rejectsSearchForProjectOutsideTrustedContext() {
        assertThatThrownBy(() -> service.search(query(5), context("tenant-1", Set.of("project-2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project");
        assertThat(index.lastQuery).isNull();
    }

    @Test
    void rejectsSelfClaimedKnowledgeSpaceBeforeEmbeddingOrVectorSearch() {
        assertThatThrownBy(() -> service.search(
                        new KnowledgeSearchQuery("Safety inspection requirements", Set.of("restricted-space"), "project-1", 5),
                        context("tenant-1", Set.of("project-1"), Set.of("allowed-space"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge space");

        assertThat(embeddingGateway.embedCalls).isZero();
        assertThat(index.lastQuery).isNull();
    }

    @Test
    void passesOnlyTrustedKnowledgeSpaceSubsetToVectorSearch() {
        service.search(
                new KnowledgeSearchQuery(
                        "Safety inspection requirements", Set.of("space-1", "restricted-space"), "project-1", 5),
                context("tenant-1", Set.of("project-1"), Set.of("space-1")));

        assertThat(index.lastQuery.allowedSpaceIds()).containsExactly("space-1");
        assertThat(index.lastQuery.allowedProjectIds()).containsExactlyInAnyOrder("project-1", IndexedChunk.GLOBAL_PROJECT_ID);
    }

    @Test
    void returnsTrustedGlobalDocumentForCurrentProjectWithoutWideningTenantOrSpaceScope() {
        repository.add(publishedChunk("global-chunk", "global-doc", "global-version", "tenant-1", "space-1", null,
                "Global safety guide", 1, "General", "Global safety evidence"));
        index.hits = List.of(hit("global-chunk", "global-doc", 0.9, "vector payload", "tenant-1", "space-1",
                IndexedChunk.GLOBAL_PROJECT_ID));

        assertThat(service.search(query(5), context("tenant-1", Set.of("project-1"))))
                .extracting(KnowledgeCitation::documentId)
                .containsExactly("global-doc");
        assertThat(index.lastQuery.allowedProjectIds())
                .containsExactlyInAnyOrder("project-1", IndexedChunk.GLOBAL_PROJECT_ID);
    }

    @Test
    void rejectsMissingKnowledgeReadPermissionBeforeEmbeddingOrVectorSearch() {
        assertThatThrownBy(() -> service.search(
                        query(5),
                        context("tenant-1", Set.of("project-1"), Set.of("space-1"), Set.of("project:read"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge:read");

        assertThat(embeddingGateway.embedCalls).isZero();
        assertThat(index.lastQuery).isNull();
    }

    @Test
    void returnsNoMoreThanRequestedTopKWhenIndexOverReturns() {
        for (int number = 1; number <= 3; number++) {
            String suffix = Integer.toString(number);
            repository.add(publishedChunk("chunk-" + suffix, "doc-" + suffix, "version-" + suffix,
                    "tenant-1", "space-1", "project-1", "Guide " + suffix, number, "Section " + suffix,
                    "Evidence " + suffix));
        }
        index.hits = List.of(
                hit("chunk-1", "doc-1", 0.99, "one", "tenant-1", "space-1", "project-1"),
                hit("chunk-2", "doc-2", 0.98, "two", "tenant-1", "space-1", "project-1"),
                hit("chunk-3", "doc-3", 0.97, "three", "tenant-1", "space-1", "project-1"));

        assertThat(service.search(query(2), context("tenant-1", Set.of("project-1"))))
                .extracting(KnowledgeCitation::documentId)
                .containsExactly("doc-1", "doc-2");
    }

    @Test
    void rejectsQueryInputOutsidePublicBounds() {
        assertThatThrownBy(() -> new KnowledgeSearchQuery(" ", Set.of("space-1"), "project-1", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("query");
        assertThatThrownBy(() -> new KnowledgeSearchQuery("safety", Set.of(), "project-1", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("allowedSpaceIds");
        assertThatThrownBy(() -> new KnowledgeSearchQuery("safety", Set.of("space-1"), "project-1", 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("topK");
        assertThatThrownBy(() -> new KnowledgeSearchQuery("safety", Set.of("space-1"), "project-1", 21))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("topK");
    }

    private static KnowledgeSearchQuery query(int topK) {
        return new KnowledgeSearchQuery("Safety inspection requirements", Set.of("space-1"), "project-1", topK);
    }

    private static AgentUserContext context(String tenantId, Set<String> projectIds) {
        return context(tenantId, projectIds, Set.of("space-1"));
    }

    private static AgentUserContext context(String tenantId, Set<String> projectIds, Set<String> knowledgeSpaceIds) {
        return context(tenantId, projectIds, knowledgeSpaceIds, Set.of("knowledge:read"));
    }

    private static AgentUserContext context(
            String tenantId, Set<String> projectIds, Set<String> knowledgeSpaceIds, Set<String> permissions) {
        return new AgentUserContext(
                tenantId, "user-1", "identity-1", permissions, projectIds, knowledgeSpaceIds);
    }

    private static VectorHit hit(
            String chunkId, String documentId, double score, String content, String tenantId, String spaceId, String projectId) {
        return new VectorHit(chunkId, documentId, score, content, Map.of(
                "tenant_id", tenantId, "space_id", spaceId, "project_id", projectId));
    }

    private static KnowledgeChunkMetadata publishedChunk(
            String chunkId, String documentId, String versionId, String tenantId, String spaceId, String projectId,
            String title, Integer pageNumber, String sectionTitle, String content) {
        return new KnowledgeChunkMetadata(
                chunkId, documentId, versionId, tenantId, spaceId, projectId, title, pageNumber, sectionTitle, content,
                true, false);
    }

    private static KnowledgeChunkMetadata unpublishedChunk(
            String chunkId, String documentId, String versionId, String tenantId, String spaceId, String projectId) {
        return new KnowledgeChunkMetadata(
                chunkId, documentId, versionId, tenantId, spaceId, projectId, "Draft guide", 1, "Draft", "Draft evidence",
                false, false);
    }

    private static KnowledgeChunkMetadata deletedChunk(
            String chunkId, String documentId, String versionId, String tenantId, String spaceId, String projectId) {
        return new KnowledgeChunkMetadata(
                chunkId, documentId, versionId, tenantId, spaceId, projectId, "Deleted guide", 1, "Deleted", "Deleted evidence",
                true, true);
    }

    private static final class RecordingRepository implements KnowledgeRepository {
        private final Map<String, KnowledgeChunkMetadata> chunks = new HashMap<>();

        void add(KnowledgeChunkMetadata chunk) {
            chunks.put(chunk.chunkId(), chunk);
        }

        @Override
        public List<KnowledgeChunkMetadata> findPublishedChunks(String tenantId, Collection<String> chunkIds) {
            return chunkIds.stream()
                    .map(chunks::get)
                    .filter(chunk -> chunk != null && chunk.tenantId().equals(tenantId))
                    .toList();
        }

        @Override
        public void save(KnowledgeDocument document) {
        }

        @Override
        public void markIndexingSucceeded(String documentId) {
        }

        @Override
        public void markIndexingFailed(String documentId, String failureCode) {
        }
    }

    private static final class RecordingVectorIndex implements VectorIndex {
        private List<VectorHit> hits = List.of();
        private VectorSearchQuery lastQuery;

        @Override
        public void upsert(List<IndexedChunk> chunks) {
        }

        @Override
        public List<VectorHit> search(VectorSearchQuery query) {
            lastQuery = query;
            return hits;
        }

        @Override
        public void deleteDocument(String tenantId, String documentId) {
        }
    }

    private static final class RecordingEmbeddingGateway implements EmbeddingGateway {
        private int embedCalls;

        @Override
        public List<Float> embed(String text) {
            embedCalls++;
            return List.of(0.25f, 0.75f);
        }

        @Override
        public String modelKey() {
            return "test";
        }
    }
}
