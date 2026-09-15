package com.smart.agent.chat;

import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunRepository;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.run.AgentRunStep;
import com.smart.agent.run.AgentRunStepRepository;
import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/agent/conversations")
@ConditionalOnProperty(name = "agent.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationQueryController {
    private final ConversationService conversations;
    private final AgentRunRepository runs;
    private final AgentRunService runService;
    private final AgentRunStepRepository steps;
    public ConversationQueryController(ConversationService conversations, AgentRunRepository runs, AgentRunService runService,
            AgentRunStepRepository steps) {
        this.conversations = conversations; this.runs = runs; this.runService = runService; this.steps = steps;
    }
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Conversation get(@PathVariable String id, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        Conversation conversation = conversations.find(c.tenantId(), c.userId(), id);
        conversation.messages().size();
        return conversation;
    }

    @GetMapping
    public List<ConversationSummary> list(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        return conversations.list(c.tenantId(), c.userId()).stream()
                .map(ConversationSummary::new)
                .collect(Collectors.toList());
    }

    public static final class ConversationSummary {
        private final String id;
        private final String title;
        private final String modelId;
        private final java.time.Instant updatedAt;
        public ConversationSummary(Conversation conversation) {
            this.id = conversation.id();
            this.title = conversation.title();
            this.modelId = conversation.modelId();
            this.updatedAt = conversation.updatedAt();
        }
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getModelId() { return modelId; }
        public java.time.Instant getUpdatedAt() { return updatedAt; }
    }

    @PostMapping
    public Conversation create(@RequestBody CreateConversationRequest request,
                               @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        String title = request == null || request.title == null || request.title.trim().isEmpty() ? "新建对话" : request.title.trim();
        String modelId = request == null ? null : request.modelId;
        return conversations.create(c.tenantId(), c.userId(), title, modelId);
    }

    @PutMapping("/{id}")
    public ConversationSummary rename(@PathVariable String id, @RequestBody CreateConversationRequest request,
                               @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        String title = request == null ? null : request.title;
        Conversation conversation = conversations.rename(c.tenantId(), c.userId(), id, title);
        return new ConversationSummary(conversation);
    }

    /**
     * 切换会话使用的模型。只能操作属于当前租户和当前用户的会话。
     */
    @PutMapping("/{id}/model")
    public ConversationSummary switchModel(@PathVariable String id, @RequestBody SwitchModelRequest request,
            @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        String modelId = request == null ? null : request.modelId;
        Conversation conversation = conversations.switchModel(c.tenantId(), c.userId(), id, modelId);
        return new ConversationSummary(conversation);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        conversations.delete(c.tenantId(), c.userId(), id);
    }

    public static class CreateConversationRequest {
        public String title;
        public String modelId;
    }

    public static class SwitchModelRequest {
        public String modelId;
    }

    @GetMapping("/{id}/runs")
    @Transactional(readOnly = true)
    public List<RunSummary> runs(@PathVariable String id, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        return runs.findByTenantIdAndUserIdAndConversationId(c.tenantId(), c.userId(), id).stream()
                .map(RunSummary::new).collect(Collectors.toList());
    }
    @GetMapping("/{id}/runs/{runId}/steps")
    @Transactional(readOnly = true)
    public List<RunStepSummary> steps(@PathVariable String id, @PathVariable String runId,
            @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        runs.findByIdAndTenantIdAndUserId(c.tenantId(), c.userId(), runId)
                .filter(run -> id.equals(run.conversationId()))
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
        return steps.findByTenantIdAndUserIdAndRunIdOrderBySequence(c.tenantId(), c.userId(), runId).stream()
                .map(RunStepSummary::new).collect(Collectors.toList());
    }

    public static final class RunSummary {
        private final String id;
        private final String traceId;
        private final String status;
        private final String modelId;
        private final String modelDisplayName;
        private final String modelName;
        private final Long modelConfigVersion;
        private final java.time.Instant createdAt;
        private final java.time.Instant updatedAt;
        RunSummary(AgentRun run) {
            this.id = run.id(); this.traceId = run.traceId(); this.status = run.status().name();
            this.modelId = run.modelId(); this.modelDisplayName = run.modelDisplayName();
            this.modelName = run.modelName(); this.modelConfigVersion = run.modelConfigVersion();
            this.createdAt = run.createdAt(); this.updatedAt = run.updatedAt();
        }
        public String getId() { return id; }
        public String getTraceId() { return traceId; }
        public String getStatus() { return status; }
        public String getModelId() { return modelId; }
        public String getModelDisplayName() { return modelDisplayName; }
        public String getModelName() { return modelName; }
        public Long getModelConfigVersion() { return modelConfigVersion; }
        public java.time.Instant getCreatedAt() { return createdAt; }
        public java.time.Instant getUpdatedAt() { return updatedAt; }
    }

    public static final class RunStepSummary {
        private final long sequence;
        private final String type;
        private final String status;
        private final String safeInputSummary;
        private final String safeOutputSummary;
        RunStepSummary(AgentRunStep step) {
            this.sequence = step.sequence(); this.type = step.type(); this.status = step.status();
            this.safeInputSummary = step.safeInputSummary(); this.safeOutputSummary = step.safeOutputSummary();
        }
        public long getSequence() { return sequence; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public String getSafeInputSummary() { return safeInputSummary; }
        public String getSafeOutputSummary() { return safeOutputSummary; }
    }
    @PostMapping("/{id}/runs/{runId}/cancel")
    public AgentRun cancel(@PathVariable String runId, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        return runService.cancel(c.tenantId(), c.userId(), runId);
    }
}
