package com.smart.agent.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ai_attachment")
public class Attachment {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AttachmentPurpose purpose;

    @Column(name = "original_filename", length = 512, nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "object_key", length = 512, nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "size_bytes")
    private Long size;

    @Column(length = 255)
    private String etag;

    @Column(name = "detected_media_type", length = 255)
    private String detectedMediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttachmentStatus status;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "upload_expires_at", nullable = false, updatable = false)
    private Instant uploadExpiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "cleanup_completed_at")
    private Instant cleanupCompletedAt;

    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "update_time", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Attachment() {
    }

    private Attachment(UUID id, String tenantId, String userId, AttachmentPurpose purpose,
            String originalFilename, String objectKey, Instant uploadExpiresAt, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.purpose = purpose;
        this.originalFilename = originalFilename;
        this.objectKey = objectKey;
        this.uploadExpiresAt = uploadExpiresAt;
        this.status = AttachmentStatus.PENDING_UPLOAD;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static Attachment register(UUID id, String tenantId, String userId, AttachmentPurpose purpose,
            String originalFilename, String objectKey, Instant uploadExpiresAt, Instant now) {
        return new Attachment(id, tenantId, userId, purpose, originalFilename, objectKey, uploadExpiresAt, now);
    }

    void complete(String objectKey, long size, String etag, String detectedMediaType, Instant now) {
        if (!this.objectKey.equals(objectKey)) {
            throw new IllegalArgumentException("Object key does not match the registered attachment");
        }
        if (status == AttachmentStatus.UPLOADED) {
            if (this.etag.equals(etag)) {
                return;
            }
            throw new IllegalStateException("Attachment was already completed with a different ETag");
        }
        requireStatus(AttachmentStatus.PENDING_UPLOAD);
        if (now.isAfter(uploadExpiresAt)) {
            throw new IllegalStateException("Attachment upload has expired");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        this.size = size;
        this.etag = requireText(etag, "etag");
        this.detectedMediaType = requireText(detectedMediaType, "detectedMediaType");
        transition(AttachmentStatus.UPLOADED, null, now);
    }

    void markProcessing(Instant now) {
        requireStatus(AttachmentStatus.UPLOADED);
        transition(AttachmentStatus.PROCESSING, null, now);
    }

    void markReady(Instant now) {
        requireStatus(AttachmentStatus.PROCESSING);
        transition(AttachmentStatus.READY, null, now);
        lastUsedAt = now;
    }

    void markUnsupported(String code, Instant now) {
        requireStatus(AttachmentStatus.PROCESSING);
        transition(AttachmentStatus.UNSUPPORTED, requireText(code, "failureCode"), now);
    }

    void quarantine(String code, Instant now) {
        requireStatus(AttachmentStatus.PROCESSING);
        transition(AttachmentStatus.QUARANTINED, requireText(code, "failureCode"), now);
    }

    void fail(String code, Instant now) {
        requireStatus(AttachmentStatus.PROCESSING);
        transition(AttachmentStatus.FAILED, requireText(code, "failureCode"), now);
    }

    void expire(Instant now) {
        if (status == AttachmentStatus.EXPIRED) {
            return;
        }
        Set<AttachmentStatus> expirable = EnumSet.of(AttachmentStatus.PENDING_UPLOAD, AttachmentStatus.UPLOADED,
                AttachmentStatus.PROCESSING, AttachmentStatus.READY, AttachmentStatus.FAILED,
                AttachmentStatus.UNSUPPORTED, AttachmentStatus.QUARANTINED);
        if (!expirable.contains(status)) {
            throw new IllegalStateException("Attachment cannot expire from " + status);
        }
        transition(AttachmentStatus.EXPIRED, failureCode, now);
    }

    void markCleanupCompleted(Instant now) {
        if (status != AttachmentStatus.EXPIRED) {
            throw new IllegalStateException("Attachment must be expired before cleanup completion");
        }
        if (cleanupCompletedAt == null) {
            cleanupCompletedAt = now;
            updatedAt = now;
        }
    }

    private void transition(AttachmentStatus next, String failureCode, Instant now) {
        status = next;
        this.failureCode = failureCode;
        updatedAt = now;
    }

    private void requireStatus(AttachmentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected attachment status " + expected + " but was " + status);
        }
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public String userId() { return userId; }
    public AttachmentPurpose purpose() { return purpose; }
    public String originalFilename() { return originalFilename; }
    public String objectKey() { return objectKey; }
    public Long size() { return size; }
    public String etag() { return etag; }
    public String detectedMediaType() { return detectedMediaType; }
    public AttachmentStatus status() { return status; }
    public String failureCode() { return failureCode; }
    public Instant uploadExpiresAt() { return uploadExpiresAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public Instant cleanupCompletedAt() { return cleanupCompletedAt; }
    public Instant updatedAt() { return updatedAt; }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
