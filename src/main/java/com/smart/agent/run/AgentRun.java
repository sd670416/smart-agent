package com.smart.agent.run;

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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ai_run")
public class AgentRun {

    private static final Map<AgentRunStatus, Set<AgentRunStatus>> ALLOWED = Map.of(
            AgentRunStatus.RECEIVED, Set.of(AgentRunStatus.ROUTING, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED),
            AgentRunStatus.ROUTING, Set.of(AgentRunStatus.PLANNING, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED),
            AgentRunStatus.PLANNING, Set.of(AgentRunStatus.TOOL_SELECTING, AgentRunStatus.RETRIEVING,
                    AgentRunStatus.GENERATING, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED),
            AgentRunStatus.TOOL_SELECTING, Set.of(AgentRunStatus.TOOL_EXECUTING, AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELLED),
            AgentRunStatus.TOOL_EXECUTING, Set.of(AgentRunStatus.RETRIEVING, AgentRunStatus.GENERATING,
                    AgentRunStatus.FAILED, AgentRunStatus.TIMEOUT, AgentRunStatus.PERMISSION_DENIED),
            AgentRunStatus.RETRIEVING, Set.of(AgentRunStatus.GENERATING, AgentRunStatus.FAILED,
                    AgentRunStatus.TIMEOUT, AgentRunStatus.PERMISSION_DENIED),
            AgentRunStatus.GENERATING, Set.of(AgentRunStatus.COMPLETED, AgentRunStatus.FAILED, AgentRunStatus.TIMEOUT),
            AgentRunStatus.WAITING_APPROVAL, Set.of(AgentRunStatus.RESUMING, AgentRunStatus.CANCELLED),
            AgentRunStatus.RESUMING, Set.of(AgentRunStatus.TOOL_EXECUTING, AgentRunStatus.FAILED,
                    AgentRunStatus.PERMISSION_DENIED));

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    @Column(name = "conversation_id", nullable = false, length = 36, updatable = false)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status;

    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "update_time", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected AgentRun() {
    }

    private AgentRun(String tenantId, String userId, String conversationId) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.conversationId = conversationId;
        this.status = AgentRunStatus.RECEIVED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static AgentRun start(String tenantId, String userId, String conversationId) {
        return new AgentRun(tenantId, userId, conversationId);
    }

    public void transition(AgentRunStatus next) {
        if (!ALLOWED.getOrDefault(status, Set.of()).contains(next)) {
            throw new IllegalStateException("Invalid agent run transition from " + status + " to " + next);
        }
        status = next;
        updatedAt = Instant.now();
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

    public String conversationId() {
        return conversationId;
    }

    public AgentRunStatus status() {
        return status;
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
