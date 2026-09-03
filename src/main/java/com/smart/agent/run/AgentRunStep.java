package com.smart.agent.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_run_step")
public class AgentRunStep {
    @Id @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    private String tenantId;
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;
    @Column(name = "run_id", length = 36, nullable = false, updatable = false)
    private String runId;
    @Column(name = "step_no", nullable = false, updatable = false)
    private long sequence;
    @Column(name = "step_type", length = 64, nullable = false)
    private String type;
    @Column(length = 32, nullable = false)
    private String status;
    @Column(name = "safe_input_summary", columnDefinition = "LONGTEXT")
    private String safeInputSummary;
    @Column(name = "safe_output_summary", columnDefinition = "LONGTEXT")
    private String safeOutputSummary;
    @Column(name = "started_time") private Instant startedAt;
    @Column(name = "finished_time") private Instant finishedAt;
    @Column(name = "create_time", nullable = false) private Instant createdAt;
    @Column(name = "update_time", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected AgentRunStep() {}

    private AgentRunStep(String tenantId, String userId, String runId, long sequence, String type,
            String status, String safeInputSummary, String safeOutputSummary) {
        this.id = UUID.randomUUID().toString(); this.tenantId = tenantId; this.userId = userId; this.runId = runId;
        this.sequence = sequence; this.type = type; this.status = status;
        this.safeInputSummary = safeInputSummary; this.safeOutputSummary = safeOutputSummary;
        this.startedAt = Instant.now(); this.finishedAt = startedAt; this.createdAt = startedAt; this.updatedAt = startedAt;
    }

    public static AgentRunStep completed(String tenantId, String userId, String runId, long sequence, String type,
            String safeInputSummary, String safeOutputSummary) {
        return new AgentRunStep(tenantId, userId, runId, sequence, type, "COMPLETED",
                safeInputSummary, safeOutputSummary);
    }

    public String runId() { return runId; }
    public long sequence() { return sequence; }
    public String type() { return type; }
    public String status() { return status; }
    public String safeInputSummary() { return safeInputSummary; }
    public String safeOutputSummary() { return safeOutputSummary; }
}
