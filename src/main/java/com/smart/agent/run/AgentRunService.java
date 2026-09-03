package com.smart.agent.run;

import com.smart.agent.conversation.ConversationRepository;
import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.Message;
import com.smart.agent.tool.ToolExecutionSummary;
import java.util.Objects;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;

@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentRunService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentRunService.class);

    private final AgentRunRepository agentRunRepository;
    private final ConversationRepository conversationRepository;
    private final AgentRunStepRepository stepRepository;

    public AgentRunService(AgentRunRepository agentRunRepository, ConversationRepository conversationRepository) {
        this(agentRunRepository, conversationRepository, (AgentRunStepRepository) null);
    }

    @Autowired
    public AgentRunService(AgentRunRepository agentRunRepository, ConversationRepository conversationRepository,
            ObjectProvider<AgentRunStepRepository> stepRepository) {
        this(agentRunRepository, conversationRepository, stepRepository.getIfAvailable());
    }

    public AgentRunService(AgentRunRepository agentRunRepository, ConversationRepository conversationRepository,
            AgentRunStepRepository stepRepository) {
        this.agentRunRepository = agentRunRepository;
        this.conversationRepository = conversationRepository;
        this.stepRepository = stepRepository;
    }

    @Transactional
    public AgentRun start(String tenantId, String userId, String conversationId) {
        return start(tenantId, userId, conversationId, null);
    }

    @Transactional
    public AgentRun start(String tenantId, String userId, String conversationId, String traceId) {
        String scopedTenantId = requireText(tenantId, "tenantId");
        String scopedUserId = requireText(userId, "userId");
        String requiredConversationId = requireText(conversationId, "conversationId");
        conversationRepository.findByIdAndTenantIdAndUserId(scopedTenantId, scopedUserId, requiredConversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + requiredConversationId));
        return agentRunRepository.save(AgentRun.start(scopedTenantId, scopedUserId, requiredConversationId, traceId));
    }

    @Transactional
    public AgentRun transition(String tenantId, String userId, String runId, AgentRunStatus expected, AgentRunStatus next) {
        AgentRun run = agentRunRepository.findByIdAndTenantIdAndUserId(
                        requireText(tenantId, "tenantId"), requireText(userId, "userId"), requireText(runId, "runId"))
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found: " + runId));
        if (run.status() != expected) {
            throw new IllegalStateException("Expected agent run status " + expected + " but was " + run.status());
        }
        run.transition(next);
        return agentRunRepository.save(run);
    }

    public void recordToolExecution(ToolExecutionSummary summary) {
        Objects.requireNonNull(summary, "summary");
        log.info("tool_execution_summary toolKey={} risk={} outcome={} durationMillis={} resultSizeBytes={}",
                summary.toolKey(), summary.risk(), summary.outcome(), summary.durationMillis(), summary.resultSizeBytes());
    }

    @Transactional
    public AgentRun recordAudit(String tenantId, String userId, String runId, int inputTokens, int outputTokens,
            String toolSummary, String citationSummary, String safeErrorCode) {
        AgentRun run = agentRunRepository.findByIdAndTenantIdAndUserId(tenantId, userId, runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found: " + runId));
        run.recordUsage(inputTokens, outputTokens);
        if (toolSummary != null) run.recordToolSummary(toolSummary);
        if (citationSummary != null) run.recordCitationSummary(citationSummary);
        if (safeErrorCode != null) run.recordSafeError(safeErrorCode);
        return agentRunRepository.save(run);
    }

    @Transactional
    public void recordStep(String tenantId, String userId, String runId, String type,
            String safeInputSummary, String safeOutputSummary) {
        if (stepRepository == null) return;
        List<AgentRunStep> existing = stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                tenantId, userId, runId);
        stepRepository.save(AgentRunStep.completed(tenantId, userId, runId, existing.size() + 1L,
                requireText(type, "type"), safeInputSummary, safeOutputSummary));
    }

    @Transactional
    public Completion completeWithAssistant(String tenantId, String userId, String conversationId, String runId,
            AgentRunStatus expected, String content, int inputTokens, int outputTokens,
            String safeModelInput, String safeModelOutput) {
        Conversation conversation = conversationRepository.findByIdAndTenantIdAndUserId(
                tenantId, userId, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        AgentRun run = agentRunRepository.findByIdAndTenantIdAndUserId(tenantId, userId, runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found: " + runId));
        if (run.status() != expected) throw new IllegalStateException("Unexpected run status");
        Message message = conversation.append(Message.Role.ASSISTANT, content);
        run.recordUsage(inputTokens, outputTokens);
        run.transition(AgentRunStatus.COMPLETED);
        conversationRepository.save(conversation);
        agentRunRepository.save(run);
        if (stepRepository != null) {
            List<AgentRunStep> existing = stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                    tenantId, userId, runId);
            long modelSequence = existing.size() + 1L;
            stepRepository.save(AgentRunStep.completed(tenantId, userId, runId, modelSequence,
                    "MODEL", safeModelInput, safeModelOutput));
            stepRepository.save(AgentRunStep.completed(tenantId, userId, runId, modelSequence + 1L,
                    "TERMINAL", null, "{\"status\":\"COMPLETED\"}"));
        }
        return new Completion(run, message);
    }

    @Transactional
    public AgentRun finishTerminal(String tenantId, String userId, String runId, AgentRunStatus expected,
            AgentRunStatus terminal, String safeErrorCode) {
        AgentRun run = agentRunRepository.findByIdAndTenantIdAndUserId(tenantId, userId, runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found: " + runId));
        if (run.status() != expected) throw new IllegalStateException("Unexpected run status");
        if (safeErrorCode != null) run.recordSafeError(safeErrorCode);
        run.transition(terminal);
        AgentRun saved = agentRunRepository.save(run);
        if (stepRepository != null) {
            List<AgentRunStep> existing = stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                    tenantId, userId, runId);
            stepRepository.save(AgentRunStep.completed(tenantId, userId, runId, existing.size() + 1L,
                    "TERMINAL", null, "{\"status\":\"" + terminal.name() + "\",\"errorCode\":"
                            + (safeErrorCode == null ? "null" : "\"" + safeErrorCode + "\"") + "}"));
        }
        return saved;
    }

    public record Completion(AgentRun run, Message message) {}

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
