package com.smart.agent.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.knowledge.IndexedChunk;
import com.smart.agent.knowledge.VectorHit;
import com.smart.agent.knowledge.VectorIndex;
import com.smart.agent.knowledge.VectorNamespace;
import com.smart.agent.knowledge.VectorSearchQuery;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachmentCleanupJobTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    private final List<String> events = new ArrayList<>();
    private final InMemoryAttachmentRepository repository = new InMemoryAttachmentRepository(events);
    private final RecordingVectorIndex vectors = new RecordingVectorIndex(events);
    private final RecordingObjectStorage objects = new RecordingObjectStorage(events);
    private final AttachmentService attachmentService = new AttachmentService(
            repository, Clock.fixed(CREATED_AT, ZoneOffset.UTC));
    private final AttachmentCleanupJob job = new AttachmentCleanupJob(
            repository, new AttachmentService(repository, Clock.fixed(NOW, ZoneOffset.UTC)),
            vectors, objects, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void cleansOnlyTheConfiguredNumberOfPagesAndUsesTheRequiredDeletionOrder() {
        Attachment first = readyChatAttachment("first.txt");
        Attachment second = readyChatAttachment("second.txt");
        Attachment third = readyChatAttachment("third.txt");
        events.clear();

        AttachmentCleanupJob.CleanupResult result = job.cleanupExpired(NOW.minusSeconds(7 * 24 * 3600), 2, 1);

        assertThat(result).isEqualTo(new AttachmentCleanupJob.CleanupResult(2, 2, 0));
        assertThat(repository.values.values()).filteredOn(value -> value.cleanupCompletedAt() != null).hasSize(2);
        assertThat(repository.values.values()).filteredOn(value -> value.status() == AttachmentStatus.READY).hasSize(1);
        assertThat(repository.values.get(third.id()).status()).isIn(AttachmentStatus.READY, AttachmentStatus.EXPIRED);
        assertThat(events).hasSize(8);
        for (int offset = 0; offset < events.size(); offset += 4) {
            String id = events.get(offset).substring("expired:".length());
            assertThat(events.subList(offset, offset + 4)).containsExactly(
                    "expired:" + id,
                    "vector:" + VectorNamespace.CHAT_ATTACHMENT + ":" + id,
                    "object:" + id,
                    "completed:" + id);
        }
        assertThat(vectors.namespaces).containsOnly(VectorNamespace.CHAT_ATTACHMENT);
        assertThat(List.of(first.id(), second.id(), third.id())).hasSize(3);
    }

    @Test
    void retriesExpiredRowsIdempotentlyAfterObjectDeletionFails() {
        Attachment attachment = readyChatAttachment("retry.txt");
        objects.failOnceFor = attachment.objectKey();
        events.clear();

        AttachmentCleanupJob.CleanupResult first = job.cleanupExpired(NOW.minusSeconds(7 * 24 * 3600), 10, 1);
        AttachmentCleanupJob.CleanupResult second = job.cleanupExpired(NOW.minusSeconds(7 * 24 * 3600), 10, 1);

        assertThat(first).isEqualTo(new AttachmentCleanupJob.CleanupResult(1, 0, 1));
        assertThat(second).isEqualTo(new AttachmentCleanupJob.CleanupResult(1, 1, 0));
        Attachment cleaned = repository.values.get(attachment.id());
        assertThat(cleaned.status()).isEqualTo(AttachmentStatus.EXPIRED);
        assertThat(cleaned.cleanupCompletedAt()).isEqualTo(NOW);
        assertThat(vectors.deletedAttachmentIds).containsExactly(
                attachment.id().toString(), attachment.id().toString());
        assertThat(events).containsSubsequence(
                "expired:" + attachment.id(),
                "vector:" + VectorNamespace.CHAT_ATTACHMENT + ":" + attachment.id(),
                "object:" + attachment.id(),
                "vector:" + VectorNamespace.CHAT_ATTACHMENT + ":" + attachment.id(),
                "object:" + attachment.id(),
                "completed:" + attachment.id());
    }

    private Attachment readyChatAttachment(String filename) {
        Attachment attachment = attachmentService.registerUpload("tenant-1", "user-1",
                AttachmentPurpose.CHAT_ATTACHMENT, filename, CREATED_AT.plusSeconds(300));
        objects.values.put(attachment.objectKey(), new byte[] {1});
        attachmentService.completeUpload(new CompleteUploadCommand(
                attachment.id(), "tenant-1", "user-1", attachment.objectKey(), 1,
                "etag-" + attachment.id(), "text/plain"));
        attachmentService.markProcessing(attachment.id(), "tenant-1", "user-1");
        return attachmentService.markReady(attachment.id(), "tenant-1", "user-1");
    }

    private static final class InMemoryAttachmentRepository implements AttachmentRepository {
        private final Map<UUID, Attachment> values = new LinkedHashMap<>();
        private final List<String> events;

        private InMemoryAttachmentRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public Attachment save(Attachment attachment) {
            values.put(attachment.id(), attachment);
            if (attachment.cleanupCompletedAt() != null) {
                events.add("completed:" + attachment.id());
            } else if (attachment.status() == AttachmentStatus.EXPIRED) {
                events.add("expired:" + attachment.id());
            }
            return attachment;
        }

        @Override
        public Optional<Attachment> findByIdAndTenantIdAndUserId(UUID id, String tenantId, String userId) {
            return Optional.ofNullable(values.get(id))
                    .filter(value -> value.tenantId().equals(tenantId) && value.userId().equals(userId));
        }

        @Override
        public List<Attachment> findExpiredChatAttachments(Instant lastUsedBefore, UUID afterId, int limit) {
            return values.values().stream()
                    .filter(value -> value.purpose() == AttachmentPurpose.CHAT_ATTACHMENT)
                    .filter(value -> value.cleanupCompletedAt() == null)
                    .filter(value -> value.status() == AttachmentStatus.EXPIRED
                            || !value.updatedAt().isAfter(lastUsedBefore))
                    .filter(value -> afterId == null || value.id().toString().compareTo(afterId.toString()) > 0)
                    .sorted(Comparator.comparing(value -> value.id().toString()))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class RecordingVectorIndex implements VectorIndex {
        private final List<String> events;
        private final List<VectorNamespace> namespaces = new ArrayList<>();
        private final List<String> deletedAttachmentIds = new ArrayList<>();

        private RecordingVectorIndex(List<String> events) {
            this.events = events;
        }

        @Override
        public void upsert(List<IndexedChunk> chunks) {
        }

        @Override
        public List<VectorHit> search(VectorSearchQuery query) {
            return List.of();
        }

        @Override
        public void deleteDocument(String tenantId, String documentId) {
        }

        @Override
        public void deleteAttachment(VectorNamespace namespace, String tenantId, String attachmentId) {
            namespaces.add(namespace);
            deletedAttachmentIds.add(attachmentId);
            events.add("vector:" + namespace + ":" + attachmentId);
        }
    }

    private static final class RecordingObjectStorage implements AttachmentObjectStorage {
        private final List<String> events;
        private final Map<String, byte[]> values = new LinkedHashMap<>();
        private String failOnceFor;

        private RecordingObjectStorage(List<String> events) {
            this.events = events;
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(values.get(objectKey));
        }

        @Override
        public void delete(String objectKey) {
            UUID id = UUID.fromString(objectKey.substring(objectKey.lastIndexOf('/') + 1, objectKey.lastIndexOf('.')));
            events.add("object:" + id);
            if (objectKey.equals(failOnceFor)) {
                failOnceFor = null;
                throw new IllegalStateException("temporary object storage failure");
            }
            values.remove(objectKey);
        }
    }
}
