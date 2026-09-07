package com.smart.agent.chat;

import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunRepository;
import com.smart.agent.run.AgentRunService;
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
    public ConversationQueryController(ConversationService conversations, AgentRunRepository runs, AgentRunService runService) {
        this.conversations = conversations; this.runs = runs; this.runService = runService;
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
                .map(item -> new ConversationSummary(item.id(), item.title()))
                .collect(Collectors.toList());
    }

    public static final class ConversationSummary {
        private final String id;
        private final String title;
        public ConversationSummary(String id, String title) { this.id = id; this.title = title; }
        public String getId() { return id; }
        public String getTitle() { return title; }
    }

    @PostMapping
    public Conversation create(@RequestBody CreateConversationRequest request,
                               @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        String title = request == null || request.title == null || request.title.trim().isEmpty() ? "新建对话" : request.title.trim();
        return conversations.create(c.tenantId(), c.userId(), title);
    }

    @PutMapping("/{id}")
    public ConversationSummary rename(@PathVariable String id, @RequestBody CreateConversationRequest request,
                               @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        String title = request == null ? null : request.title;
        Conversation conversation = conversations.rename(c.tenantId(), c.userId(), id, title);
        return new ConversationSummary(conversation.id(), conversation.title());
    }

    public static class CreateConversationRequest { public String title; }
    @GetMapping("/{id}/runs")
    public List<AgentRun> runs(@PathVariable String id, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        return runs.findByTenantIdAndUserIdAndConversationId(c.tenantId(), c.userId(), id);
    }
    @PostMapping("/{id}/runs/{runId}/cancel")
    public AgentRun cancel(@PathVariable String runId, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        return runService.cancel(c.tenantId(), c.userId(), runId);
    }
}
