package com.smart.agent.clarification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_pending_clarification")
public class PendingClarification {
    public enum Status { PENDING, RESOLVED, EXPIRED, CANCELLED }

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    private String tenantId;
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;
    @Column(name = "conversation_id", length = 36, nullable = false, updatable = false)
    private String conversationId;
    @Column(name = "source_domain", length = 64)
    private String sourceDomain;
    @Column(name = "intent_json", nullable = false, columnDefinition = "LONGTEXT")
    private String intentJson;
    @Column(name = "options_json", nullable = false, columnDefinition = "LONGTEXT")
    private String optionsJson;
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private Status status;
    @Column(name = "selected_option_id", length = 128)
    private String selectedOptionId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "update_time", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private Long version;

    protected PendingClarification() {}

    private PendingClarification(String tenantId, String userId, String conversationId,
            String sourceDomain, String intentJson, String optionsJson, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = requireText(tenantId, "tenantId");
        this.userId = requireText(userId, "userId");
        this.conversationId = requireText(conversationId, "conversationId");
        this.sourceDomain = blankToNull(sourceDomain);
        this.intentJson = requireText(intentJson, "intentJson");
        this.optionsJson = requireText(optionsJson, "optionsJson");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null");
        this.expiresAt = expiresAt;
        this.status = Status.PENDING;
    }

    public static PendingClarification create(String tenantId, String userId, String conversationId,
            String sourceDomain, String intentJson, String optionsJson, Instant expiresAt) {
        return new PendingClarification(tenantId, userId, conversationId,
                sourceDomain, intentJson, optionsJson, expiresAt);
    }

    public boolean belongsTo(String tenantId, String userId, String conversationId) {
        return this.tenantId.equals(tenantId) && this.userId.equals(userId)
                && this.conversationId.equals(conversationId);
    }

    public boolean expireIfNecessary(Instant now) {
        if (status == Status.PENDING && !expiresAt.isAfter(now)) {
            status = Status.EXPIRED;
            updatedAt = now;
            return true;
        }
        return false;
    }

    public void resolve(String optionId, Instant now) {
        requirePending(now);
        selectedOptionId = requireText(optionId, "optionId");
        status = Status.RESOLVED;
        resolvedAt = now;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status != Status.PENDING) return;
        status = Status.CANCELLED;
        updatedAt = now;
    }

    public void requirePending(Instant now) {
        expireIfNecessary(now);
        if (status != Status.PENDING) {
            throw new PendingClarificationException("待确认问题不存在或已失效，请重新提问");
        }
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String userId() { return userId; }
    public String conversationId() { return conversationId; }
    public String sourceDomain() { return sourceDomain; }
    public String intentJson() { return intentJson; }
    public String optionsJson() { return optionsJson; }
    public Status status() { return status; }
    public String selectedOptionId() { return selectedOptionId; }
    public Instant expiresAt() { return expiresAt; }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = Instant.now(); }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
