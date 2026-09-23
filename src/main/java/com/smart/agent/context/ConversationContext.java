package com.smart.agent.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversation_context")
public class ConversationContext {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "conversation_id", length = 36, nullable = false, updatable = false)
    private String conversationId;
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    private String tenantId;
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;
    @Column(name = "context_type", length = 64, nullable = false, updatable = false)
    private String contextType;
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;
    @Column(name = "source_run_id", length = 36)
    private String sourceRunId;
    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "update_time", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private Long version;

    protected ConversationContext() {}

    private ConversationContext(String tenantId, String userId, String conversationId,
            String contextType, String payloadJson, String sourceRunId) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = requireText(tenantId, "tenantId");
        this.userId = requireText(userId, "userId");
        this.conversationId = requireText(conversationId, "conversationId");
        this.contextType = requireText(contextType, "contextType");
        update(payloadJson, sourceRunId);
    }

    public static ConversationContext create(String tenantId, String userId, String conversationId,
            String contextType, String payloadJson, String sourceRunId) {
        return new ConversationContext(tenantId, userId, conversationId, contextType, payloadJson, sourceRunId);
    }

    public void update(String payloadJson, String sourceRunId) {
        this.payloadJson = requireText(payloadJson, "payloadJson");
        this.sourceRunId = sourceRunId;
        this.updatedAt = Instant.now();
    }

    public String id() { return id; }
    public String conversationId() { return conversationId; }
    public String tenantId() { return tenantId; }
    public String userId() { return userId; }
    public String contextType() { return contextType; }
    public String payloadJson() { return payloadJson; }
    public String sourceRunId() { return sourceRunId; }
    public Instant updatedAt() { return updatedAt; }

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
}
