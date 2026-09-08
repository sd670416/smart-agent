package com.smart.agent.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T08:00:00Z");

    private InMemoryAttachmentRepository repository;
    private AttachmentService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttachmentRepository();
        service = new AttachmentService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersServerOwnedObjectKeyAndCompletesUploadIdempotently() {
        Attachment attachment = service.registerUpload("tenant-1", "user-1",
                AttachmentPurpose.CHAT_ATTACHMENT, "现场 照片.PnG", NOW.plusSeconds(300));

        assertThat(attachment.objectKey()).matches(
                "ai/tenant-1/chat-attachment/2026/09/04/user-1/[0-9a-f-]{36}\\.png");

        CompleteUploadCommand command = new CompleteUploadCommand(attachment.id(), "tenant-1", "user-1",
                attachment.objectKey(), 128L, "etag-1", "image/png");
        Attachment completed = service.completeUpload(command);
        Attachment repeated = service.completeUpload(command);

        assertThat(completed.status()).isEqualTo(AttachmentStatus.READY);
        assertThat(repeated.status()).isEqualTo(AttachmentStatus.READY);
        assertThat(repeated.etag()).isEqualTo("etag-1");
    }

    @Test
    void rejectsDirectReadyTransitionAndDifferentCompletionIdentity() {
        Attachment attachment = register();

        assertThatThrownBy(() -> service.markReady(attachment.id(), "tenant-1", "user-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(attachment.id(),
                "tenant-1", "user-1", "ai/tenant-1/chat-attachment/forged.txt", 1L, "etag-1", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class);

        service.completeUpload(commandFor(attachment, "etag-1"));
        assertThatThrownBy(() -> service.completeUpload(commandFor(attachment, "etag-2")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(attachment.id(),
                "tenant-1", "user-1", "ai/tenant-1/chat-attachment/forged.txt", 1L, "etag-1", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesTenantAndUserOwnershipForEveryMutation() {
        Attachment attachment = register();

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(attachment.id(),
                "tenant-2", "user-1", attachment.objectKey(), 1L, "etag-1", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(attachment.id(),
                "tenant-1", "user-2", attachment.objectKey(), 1L, "etag-1", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCompletionAfterUploadDeadline() {
        Attachment attachment = service.registerUpload("tenant-1", "user-1",
                AttachmentPurpose.CHAT_ATTACHMENT, "late.txt", NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.completeUpload(commandFor(attachment, "etag-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supportsProcessingTerminalAndExpiryTransitions() {
        Attachment ready = registerAndProcess();
        assertThat(service.markReady(ready.id(), "tenant-1", "user-1").status()).isEqualTo(AttachmentStatus.READY);
        assertThat(service.expire(ready.id(), "tenant-1", "user-1").status()).isEqualTo(AttachmentStatus.EXPIRED);

        Attachment unsupported = registerAndProcess();
        assertThat(service.markUnsupported(unsupported.id(), "tenant-1", "user-1", "UNSUPPORTED_TYPE").status())
                .isEqualTo(AttachmentStatus.UNSUPPORTED);

        Attachment quarantined = registerAndProcess();
        assertThat(service.quarantine(quarantined.id(), "tenant-1", "user-1", "CONTENT_MISMATCH").status())
                .isEqualTo(AttachmentStatus.QUARANTINED);

        Attachment failed = registerAndProcess();
        assertThat(service.fail(failed.id(), "tenant-1", "user-1", "PARSER_FAILED").status())
                .isEqualTo(AttachmentStatus.FAILED);
    }

    private Attachment register() {
        return service.registerUpload("tenant-1", "user-1", AttachmentPurpose.CHAT_ATTACHMENT,
                "notes.txt", NOW.plusSeconds(300));
    }

    private Attachment registerAndProcess() {
        Attachment attachment = register();
        service.completeUpload(commandFor(attachment, "etag-" + attachment.id()));
        return service.markProcessing(attachment.id(), "tenant-1", "user-1");
    }

    private CompleteUploadCommand commandFor(Attachment attachment, String etag) {
        return new CompleteUploadCommand(attachment.id(), "tenant-1", "user-1", attachment.objectKey(),
                32L, etag, "text/plain");
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
}
