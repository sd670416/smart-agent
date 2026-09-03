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
            AgentRunStatus.RECEIVED, Set.of(AgentRunStatus.ROUTING, AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELLED, AgentRunStatus.PERMISSION_DENIED),
            AgentRunStatus.ROUTING, Set.of(AgentRunStatus.PLANNING, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED),
            AgentRunStatus.PLANNING, Set.of(AgentRunStatus.TOOL_SELECTING, AgentRunStatus.RETRIEVING,
                    AgentRunStatus.GENERATING, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED),
            AgentRunStatus.TOOL_SELECTING, Set.of(AgentRunStatus.TOOL_EXECUTING, AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELLED),
            AgentRunStatus.TOOL_EXECUTING, Set.of(AgentRunStatus.TOOL_SELECTING, AgentRunStatus.RETRIEVING, AgentRunStatus.GENERATING,
                    AgentRunStatus.FAILED, AgentRunStatus.CANCELLED, AgentRunStatus.TIMEOUT,
                    AgentRunStatus.PERMISSION_DENIED),
            AgentRunStatus.RETRIEVING, Set.of(AgentRunStatus.GENERATING, AgentRunStatus.FAILED,
                    AgentRunStatus.TIMEOUT, AgentRunStatus.PERMISSION_DENIED),
            AgentRunStatus.GENERATING, Set.of(AgentRunStatus.TOOL_SELECTING, AgentRunStatus.COMPLETED,
                    AgentRunStatus.FAILED, AgentRunStatus.TIMEOUT, AgentRunStatus.CANCELLED),
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

    @Column(name = "trace_id", length = 128, updatable = false)
    private String traceId;

    @Column(name = "safe_error_code", length = 128)
    private String safeErrorCode;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "tool_execution_summaries", columnDefinition = "LONGTEXT")
    private String toolExecutionSummaries;

    @Column(name = "citation_summaries", columnDefinition = "LONGTEXT")
    private String citationSummaries;

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

    private AgentRun(String tenantId, String userId, String conversationId, String traceId) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.conversationId = conversationId;
        this.traceId = traceId;
        this.toolExecutionSummaries = "";
        this.citationSummaries = "";
        this.status = AgentRunStatus.RECEIVED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static AgentRun start(String tenantId, String userId, String conversationId) {
        return start(tenantId, userId, conversationId, null);
    }

    public static AgentRun start(String tenantId, String userId, String conversationId, String traceId) {
        return new AgentRun(requireText(tenantId, "tenantId"), requireText(userId, "userId"),
                requireText(conversationId, "conversationId"), traceId);
    }

    public void transition(AgentRunStatus next) {
        boolean forcedTerminal = (next == AgentRunStatus.CANCELLED || next == AgentRunStatus.TIMEOUT)
                && !isTerminal(status);
        if (!forcedTerminal && !ALLOWED.getOrDefault(status, Set.of()).contains(next)) {
            throw new IllegalStateException("Invalid agent run transition from " + status + " to " + next);
        }
        status = next;
        updatedAt = Instant.now();
    }

    private static boolean isTerminal(AgentRunStatus value) {
        return value == AgentRunStatus.COMPLETED || value == AgentRunStatus.FAILED
                || value == AgentRunStatus.CANCELLED || value == AgentRunStatus.TIMEOUT
                || value == AgentRunStatus.PERMISSION_DENIED;
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

    public void recordUsage(int input, int output) {
        inputTokens += Math.max(0, input);
        outputTokens += Math.max(0, output);
    }

    public void recordSafeError(String code) { safeErrorCode = code; }

    public void recordToolSummary(String summary) {
        toolExecutionSummaries = appendLine(toolExecutionSummaries, requireText(summary, "summary"));
    }

    public void recordCitationSummary(String summary) {
        citationSummaries = appendLine(citationSummaries, requireText(summary, "summary"));
    }

    public String traceId() { return traceId; }
    public String safeErrorCode() { return safeErrorCode; }
    public int inputTokens() { return inputTokens; }
    public int outputTokens() { return outputTokens; }
    public String toolExecutionSummaries() { return toolExecutionSummaries == null ? "" : toolExecutionSummaries; }
    public String citationSummaries() { return citationSummaries == null ? "" : citationSummaries; }

    private static String appendLine(String existing, String value) {
        return existing == null || existing.isBlank() ? value : existing + "\n" + value;
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
