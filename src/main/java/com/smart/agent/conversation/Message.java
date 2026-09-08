package com.smart.agent.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;

@Entity
@Table(name = "ai_message")
public class Message {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "attachments_json", columnDefinition = "LONGTEXT")
    private String attachmentsJson;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private long sequence;

    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "update_time", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Message() {
    }

    private Message(Conversation conversation, long sequence, Role role, String content, String attachmentsJson) {
        this.id = UUID.randomUUID().toString();
        this.conversation = conversation;
        this.tenantId = conversation.tenantId();
        this.userId = conversation.userId();
        this.sequence = sequence;
        this.role = role;
        this.content = content;
        this.attachmentsJson = attachmentsJson;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    static Message create(Conversation conversation, long sequence, Role role, String content) {
        return create(conversation, sequence, role, content, null);
    }

    static Message create(Conversation conversation, long sequence, Role role, String content, String attachmentsJson) {
        return new Message(conversation, sequence, role, content, attachmentsJson);
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("sequence")
    public long sequence() {
        return sequence;
    }

    @JsonProperty("role")
    public Role role() {
        return role;
    }

    @JsonProperty("content")
    public String content() {
        return content;
    }

    @JsonProperty("attachments")
    public List<String> attachments() {
        if (attachmentsJson == null || attachmentsJson.isBlank()) return List.of();
        return Arrays.stream(attachmentsJson.split("\\n", -1)).filter(value -> !value.isBlank()).toList();
    }

    @PrePersist
    void initializeTimestamp() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
