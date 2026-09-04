package com.smart.agent.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.attachment.Attachment;
import com.smart.agent.attachment.AttachmentObjectStorage;
import com.smart.agent.attachment.AttachmentPurpose;
import com.smart.agent.attachment.AttachmentRepository;
import com.smart.agent.attachment.AttachmentService;
import com.smart.agent.attachment.AttachmentStatus;
import com.smart.agent.attachment.CompleteUploadCommand;
import com.smart.agent.knowledge.IndexedChunk;
import com.smart.agent.knowledge.KnowledgeChunkMetadata;
import com.smart.agent.knowledge.KnowledgeDocument;
import com.smart.agent.knowledge.KnowledgeIngestionService;
import com.smart.agent.knowledge.KnowledgeRepository;
import com.smart.agent.knowledge.LocalHashEmbeddingGateway;
import com.smart.agent.knowledge.VectorHit;
import com.smart.agent.knowledge.VectorIndex;
import com.smart.agent.knowledge.VectorNamespace;
import com.smart.agent.knowledge.VectorSearchQuery;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DocumentIngestionJobTest {

    private final InMemoryAttachmentRepository attachments = new InMemoryAttachmentRepository();
    private final AttachmentService attachmentService = new AttachmentService(attachments);
    private final RecordingKnowledgeRepository knowledge = new RecordingKnowledgeRepository();
    private final RecordingVectorIndex vectors = new RecordingVectorIndex();
    private final KnowledgeIngestionService ingestion = new KnowledgeIngestionService(
            knowledge, new LocalHashEmbeddingGateway(), vectors);
    private final InMemoryObjectStorage objects = new InMemoryObjectStorage();
    private final DocumentIngestionJob job = new DocumentIngestionJob(
            attachments, attachmentService, objects, DocumentParserRegistry.defaults(), ingestion,
            new ParseLimits(1_000_000, 100_000, 100, 1_000, 5_000_000));

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void exposesAnAsyncIngestionEntryPoint() throws Exception {
        assertThat(DocumentIngestionJob.class.getMethod("ingest", DocumentIngestionCommand.class)
                .getAnnotation(Async.class)).isNotNull();
    }

    @Test
    void ingestsManagedKnowledgeWithLocationAndCompleteVectorMetadata() {
        Attachment attachment = uploaded(AttachmentPurpose.KNOWLEDGE_DOCUMENT, "guide.md",
                "# Safety\nInspect lifting equipment before use.");
        beginTransactionSynchronization();

        job.ingest(command(attachment, "doc-1", "version-1", null)).join();
        commitSynchronizations();

        assertThat(attachments.values.get(attachment.id()).status()).isEqualTo(AttachmentStatus.READY);
        assertThat(knowledge.saved).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("doc-1");
            assertThat(document.versionId()).isEqualTo("version-1");
            assertThat(document.attachmentId()).isEqualTo(attachment.id().toString());
            assertThat(document.chunks()).extracting(chunk -> chunk.sectionTitle()).containsOnly("Safety");
        });
        assertThat(vectors.upserts).singleElement().satisfies(batch -> {
            assertThat(batch.namespace).isEqualTo(VectorNamespace.KNOWLEDGE);
            assertThat(batch.chunks).singleElement().satisfies(chunk -> {
                assertThat(chunk.tenantId()).isEqualTo("tenant-1");
                assertThat(chunk.spaceId()).isEqualTo("space-1");
                assertThat(chunk.projectId()).isEqualTo("project-1");
                assertThat(chunk.documentId()).isEqualTo("doc-1");
                assertThat(chunk.documentVersionId()).isEqualTo("version-1");
                assertThat(chunk.status()).isEqualTo("draft");
                assertThat(chunk.attachmentId()).isEqualTo(attachment.id().toString());
                assertThat(chunk.expiresAt()).isNull();
                assertThat(chunk.sectionTitle()).isEqualTo("Safety");
            });
        });
    }

    @Test
    void isolatesTemporaryConversationVectorsAndCarriesTheirExpiry() {
        Attachment attachment = uploaded(AttachmentPurpose.CHAT_ATTACHMENT, "notes.txt", "temporary context");
        Instant expiresAt = Instant.now().plusSeconds(600);
        beginTransactionSynchronization();

        job.ingest(command(attachment, "chat-doc-1", "chat-version-1", expiresAt)).join();
        commitSynchronizations();

        assertThat(vectors.upserts).singleElement().satisfies(batch -> {
            assertThat(batch.namespace).isEqualTo(VectorNamespace.CHAT_ATTACHMENT);
            assertThat(batch.chunks).singleElement().satisfies(chunk -> {
                assertThat(chunk.attachmentId()).isEqualTo(attachment.id().toString());
                assertThat(chunk.documentVersionId()).isEqualTo("chat-version-1");
                assertThat(chunk.expiresAt()).isEqualTo(expiresAt);
            });
        });
    }

    @Test
    void mapsUnknownAndExecutableContentToTerminalAttachmentStatesWithoutIndexing() {
        Attachment unknown = uploaded(AttachmentPurpose.CHAT_ATTACHMENT, "payload.bin", new byte[] {0, 1, 2, 3});
        Attachment executable = uploaded(AttachmentPurpose.CHAT_ATTACHMENT, "report.pdf",
                new byte[] {'M', 'Z', 0, 0});

        job.ingest(command(unknown, "unknown-doc", "unknown-version", Instant.now().plusSeconds(600))).join();
        job.ingest(command(executable, "exe-doc", "exe-version", Instant.now().plusSeconds(600))).join();

        assertThat(attachments.values.get(unknown.id()).status()).isEqualTo(AttachmentStatus.UNSUPPORTED);
        assertThat(attachments.values.get(unknown.id()).failureCode()).isEqualTo("UNSUPPORTED_TYPE");
        assertThat(attachments.values.get(executable.id()).status()).isEqualTo(AttachmentStatus.QUARANTINED);
        assertThat(attachments.values.get(executable.id()).failureCode()).isEqualTo("EXECUTABLE_CONTENT");
        assertThat(knowledge.saved).isEmpty();
        assertThat(vectors.upserts).isEmpty();
    }

    private Attachment uploaded(AttachmentPurpose purpose, String filename, String content) {
        return uploaded(purpose, filename, content.getBytes(StandardCharsets.UTF_8));
    }

    private Attachment uploaded(AttachmentPurpose purpose, String filename, byte[] content) {
        Attachment attachment = attachmentService.registerUpload(
                "tenant-1", "user-1", purpose, filename, Instant.now().plusSeconds(300));
        objects.values.put(attachment.objectKey(), content);
        return attachmentService.completeUpload(new CompleteUploadCommand(
                attachment.id(), "tenant-1", "user-1", attachment.objectKey(), content.length,
                "etag-" + attachment.id(), "application/octet-stream"));
    }

    private static DocumentIngestionCommand command(
            Attachment attachment, String documentId, String versionId, Instant expiresAt) {
        return new DocumentIngestionCommand(attachment.id(), "tenant-1", "user-1", "space-1", "org-1",
                "project-1", documentId, versionId, attachment.originalFilename(), "draft", expiresAt);
    }

    private static void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void commitSynchronizations() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static final class InMemoryObjectStorage implements AttachmentObjectStorage {
        private final Map<String, byte[]> values = new LinkedHashMap<>();

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(values.get(objectKey));
        }

        @Override
        public void delete(String objectKey) {
            values.remove(objectKey);
        }
    }

    private static final class InMemoryAttachmentRepository implements AttachmentRepository {
        private final Map<UUID, Attachment> values = new LinkedHashMap<>();

        @Override
        public Attachment save(Attachment attachment) {
            values.put(attachment.id(), attachment);
            return attachment;
        }

        @Override
        public Optional<Attachment> findByIdAndTenantIdAndUserId(UUID id, String tenantId, String userId) {
            return Optional.ofNullable(values.get(id))
                    .filter(value -> value.tenantId().equals(tenantId) && value.userId().equals(userId));
        }
    }

    private static final class RecordingKnowledgeRepository implements KnowledgeRepository {
        private final List<KnowledgeDocument> saved = new ArrayList<>();

        @Override
        public void save(KnowledgeDocument document) {
            saved.add(document);
        }

        @Override
        public void markIndexingSucceeded(String documentId) {
        }

        @Override
        public void markIndexingFailed(String documentId, String failureCode) {
        }

        @Override
        public List<KnowledgeChunkMetadata> findPublishedChunks(String tenantId, Collection<String> chunkIds) {
            return List.of();
        }
    }

    private static final class RecordingVectorIndex implements VectorIndex {
        private final List<UpsertBatch> upserts = new ArrayList<>();

        @Override
        public void upsert(List<IndexedChunk> chunks) {
            upsert(VectorNamespace.KNOWLEDGE, chunks);
        }

        @Override
        public void upsert(VectorNamespace namespace, List<IndexedChunk> chunks) {
            upserts.add(new UpsertBatch(namespace, List.copyOf(chunks)));
        }

        @Override
        public List<VectorHit> search(VectorSearchQuery query) {
            return List.of();
        }

        @Override
        public void deleteDocument(String tenantId, String documentId) {
        }
    }

    private record UpsertBatch(VectorNamespace namespace, List<IndexedChunk> chunks) {
    }
}
