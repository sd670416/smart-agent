package com.smart.agent.conversation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_conversation")
public class Conversation {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    @Column(nullable = false, length = 255)
    private String title;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<Message> messages = new ArrayList<>();

    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "update_time", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Conversation() {
    }

    private Conversation(String id, String tenantId, String userId, String title) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.title = title;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Conversation create(String tenantId, String userId, String title) {
        return new Conversation(UUID.randomUUID().toString(), tenantId, userId, title);
    }

    public Message append(Message.Role role, String content) {
        Message message = Message.create(this, messages.size() + 1L, role, content);
        messages.add(message);
        updatedAt = Instant.now();
        return message;
    }

    public String id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public String title() {
        return title;
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
