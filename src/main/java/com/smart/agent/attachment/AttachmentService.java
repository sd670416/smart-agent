package com.smart.agent.attachment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd")
            .withZone(ZoneOffset.UTC);

    private final AttachmentRepository repository;
    private final Clock clock;

    @Autowired
    public AttachmentService(AttachmentRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AttachmentService(AttachmentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Attachment registerUpload(String tenantId, String userId, AttachmentPurpose purpose,
            String originalFilename, Instant uploadExpiresAt) {
        String tenant = Attachment.requireText(tenantId, "tenantId");
        String user = Attachment.requireText(userId, "userId");
        if (purpose == null) throw new IllegalArgumentException("purpose must not be null");
        String filename = Attachment.requireText(originalFilename, "originalFilename");
        if (uploadExpiresAt == null) throw new IllegalArgumentException("uploadExpiresAt must not be null");
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String key = "ai/" + safePathPart(tenant, "tenantId") + "/" + purpose.objectKeySegment() + "/"
                + DATE_PATH.format(now) + "/" + safePathPart(user, "userId") + "/" + id + extension(filename);
        return repository.save(Attachment.register(id, tenant, user, purpose, filename, key, uploadExpiresAt, now));
    }

    @Transactional
    public Attachment completeUpload(CompleteUploadCommand command) {
        if (command == null || command.attachmentId() == null) {
            throw new IllegalArgumentException("attachmentId must not be null");
        }
        Attachment attachment = owned(command.attachmentId(), command.tenantId(), command.userId());
        attachment.complete(command.objectKey(), command.size(), command.etag(), command.detectedMediaType(), clock.instant());
        return repository.save(attachment);
    }

    @Transactional
    public Attachment markProcessing(UUID id, String tenantId, String userId) {
        Attachment attachment = owned(id, tenantId, userId);
        attachment.markProcessing(clock.instant());
        return repository.save(attachment);
    }

    @Transactional
    public Attachment markReady(UUID id, String tenantId, String userId) {
        Attachment attachment = owned(id, tenantId, userId);
        attachment.markReady(clock.instant());
        return repository.save(attachment);
    }

    @Transactional
    public Attachment markUnsupported(UUID id, String tenantId, String userId, String code) {
        Attachment attachment = owned(id, tenantId, userId);
        attachment.markUnsupported(code, clock.instant());
        return repository.save(attachment);
    }

    @Transactional
    public Attachment quarantine(UUID id, String tenantId, String userId, String code) {
        Attachment attachment = owned(id, tenantId, userId);
        attachment.quarantine(code, clock.instant());
        return repository.save(attachment);
    }

    @Transactional
    public Attachment fail(UUID id, String tenantId, String userId, String code) {
        Attachment attachment = owned(id, tenantId, userId);
        attachment.fail(code, clock.instant());
        return repository.save(attachment);
    }

    @Transactional
    public Attachment expire(UUID id, String tenantId, String userId) {
        Attachment attachment = owned(id, tenantId, userId);
        attachment.expire(clock.instant());
        return repository.save(attachment);
    }

    private Attachment owned(UUID id, String tenantId, String userId) {
        if (id == null) throw new IllegalArgumentException("attachmentId must not be null");
        String tenant = Attachment.requireText(tenantId, "tenantId");
        String user = Attachment.requireText(userId, "userId");
        return repository.findByIdAndTenantIdAndUserId(id, tenant, user)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
    }

    private static String safePathPart(String value, String name) {
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(name + " contains unsafe path characters");
        }
        return value;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String candidate = filename.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return candidate.isEmpty() ? "" : "." + candidate.substring(0, Math.min(candidate.length(), 16));
    }
}
