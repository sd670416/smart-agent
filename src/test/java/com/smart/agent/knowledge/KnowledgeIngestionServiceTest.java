package com.smart.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class KnowledgeIngestionServiceTest {

    private final RecordingRepository repository = new RecordingRepository();
    private final RecordingVectorIndex vectorIndex = new RecordingVectorIndex();
    private final KnowledgeIngestionService service = new KnowledgeIngestionService(
            repository, new LocalHashEmbeddingGateway(), vectorIndex);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void rejectsBlankAndUtf8TextLargerThanOneMegabyte() {
        assertThatThrownBy(() -> service.ingestText(command(" \r\n ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> service.ingestText(command("界".repeat(350_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 MB");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void refusesToPersistWhenNoTransactionIsActive() {
        assertThatThrownBy(() -> service.ingestText(command("valid text")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction");
        assertThat(repository.saved).isEmpty();
        assertThat(vectorIndex.upserts).isEmpty();
    }

    @Test
    void normalizesNewlinesPersistsChecksumAndIndexesOnlyAfterCommit() {
        beginTransactionSynchronization();

        String documentId = service.ingestText(command("A\r\nB\rC"));

        assertThat(repository.saved).hasSize(1);
        KnowledgeDocument document = repository.saved.getFirst();
        assertThat(document.id()).isEqualTo(documentId);
        assertThat(document.sourceText()).isEqualTo("A\nB\nC");
        assertThat(document.sourceChecksum())
                .isEqualTo("2e70d7238a20934f7a8a145e8750ee44d6e043a7a8c9b3a1a0979d640e62af8c");
        assertThat(document.parserVersion()).isEqualTo("plain-text-v1");
        assertThat(document.embeddingModelKey()).isEqualTo("local-hash-v1");
        assertThat(vectorIndex.upserts).isEmpty();

        commitSynchronizations();

        assertThat(vectorIndex.upserts).hasSize(1);
        assertThat(repository.succeededDocumentIds).containsExactly(documentId);
    }

    @Test
    void chunksByUnicodeCodePointWithAtMost1200And150Overlap() {
        beginTransactionSynchronization();

        service.ingestText(command("😀".repeat(1_400)));

        List<KnowledgeChunk> chunks = repository.saved.getFirst().chunks();
        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().codePointCount(0, chunk.content().length())).isLessThanOrEqualTo(1_200));
        String firstSuffix = suffixByCodePoint(chunks.get(0).content(), 150);
        String secondPrefix = prefixByCodePoint(chunks.get(1).content(), 150);
        assertThat(secondPrefix).isEqualTo(firstSuffix);
        assertThat(chunks).extracting(KnowledgeChunk::ordinal).containsExactly(0, 1);
        assertThat(chunks).extracting(KnowledgeChunk::vectorPointId)
                .doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isNotBlank());
    }

    @Test
    void storesLeadingHeadingAsStableChunkSectionTitle() {
        beginTransactionSynchronization();

        service.ingestText(command("# Safety\n\nInspect lifting equipment before use."));

        assertThat(repository.saved.getFirst().chunks())
                .extracting(KnowledgeChunk::sectionTitle)
                .containsExactly("Safety");
    }

    @Test
    void deterministicMetadataMakesRepeatedIngestionIdempotent() {
        beginTransactionSynchronization();
        String firstId = service.ingestText(command("same text"));
        clearSynchronizationOnly();
        beginTransactionSynchronization();
        String secondId = service.ingestText(command("same text"));

        assertThat(secondId).isEqualTo(firstId);
        assertThat(repository.saved.get(0).versionId()).isEqualTo(repository.saved.get(1).versionId());
        assertThat(repository.saved.get(0).chunks()).extracting(KnowledgeChunk::id)
                .containsExactlyElementsOf(repository.saved.get(1).chunks().stream().map(KnowledgeChunk::id).toList());
    }

    @Test
    void recordsFailedIndexingAfterCommitWithoutLosingAuthoritativeMetadata() {
        beginTransactionSynchronization();
        vectorIndex.failure = new VectorIndexException("VECTOR_INDEX_UPSERT_FAILED", "Vector upsert failed");
        String documentId = service.ingestText(command("persist first"));

        assertThatThrownBy(KnowledgeIngestionServiceTest::commitSynchronizations)
                .isInstanceOf(VectorIndexException.class)
                .hasMessage("Vector upsert failed");
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.failedDocumentIds).containsExactly(documentId);
        assertThat(repository.succeededDocumentIds).isEmpty();
    }

    private static IngestTextCommand command(String text) {
        return new IngestTextCommand(
                "tenant-1", "space-1", "org-1", "project-1", "Safety guide", text, "published", "user-1");
    }

    private static void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void commitSynchronizations() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void clearSynchronizationOnly() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static String prefixByCodePoint(String value, int count) {
        return value.substring(0, value.offsetByCodePoints(0, count));
    }

    private static String suffixByCodePoint(String value, int count) {
        int start = value.offsetByCodePoints(value.length(), -count);
        return value.substring(start);
    }

    private static final class RecordingRepository implements KnowledgeRepository {
        private final List<KnowledgeDocument> saved = new ArrayList<>();
        private final List<String> succeededDocumentIds = new ArrayList<>();
        private final List<String> failedDocumentIds = new ArrayList<>();

        @Override
        public void save(KnowledgeDocument document) {
            saved.add(document);
        }

        @Override
        public void markIndexingSucceeded(String documentId) {
            succeededDocumentIds.add(documentId);
        }

        @Override
        public void markIndexingFailed(String documentId, String failureCode) {
            failedDocumentIds.add(documentId);
        }
    }

    private static final class RecordingVectorIndex implements VectorIndex {
        private final List<List<IndexedChunk>> upserts = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void upsert(List<IndexedChunk> chunks) {
            if (failure != null) {
                throw failure;
            }
            upserts.add(List.copyOf(chunks));
        }

        @Override
        public List<VectorHit> search(VectorSearchQuery query) {
            return List.of();
        }

        @Override
        public void deleteDocument(String tenantId, String documentId) {
        }
    }
}
