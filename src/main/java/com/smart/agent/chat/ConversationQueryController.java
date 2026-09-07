package com.smart.agent.chat;

import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunRepository;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.web.bind.annotation.*;
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
    public Conversation get(@PathVariable String id, @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        return conversations.find(c.tenantId(), c.userId(), id);
    }

    @GetMapping
    public List<Conversation> list(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        return conversations.list(c.tenantId(), c.userId());
    }

    @PostMapping
    public Conversation create(@RequestBody CreateConversationRequest request,
                               @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c) {
        String title = request == null || request.title == null || request.title.trim().isEmpty() ? "新建对话" : request.title.trim();
        return conversations.create(c.tenantId(), c.userId(), title);
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
