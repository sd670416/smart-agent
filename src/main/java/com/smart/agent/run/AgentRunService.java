package com.smart.agent.run;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(EntityManagerFactory.class)
public class AgentRunService {

    private final AgentRunRepository agentRunRepository;

    public AgentRunService(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    @Transactional
    public AgentRun start(String tenantId, String userId, String conversationId) {
        return agentRunRepository.save(AgentRun.start(tenantId, userId, conversationId));
    }

    @Transactional
    public AgentRun transition(String runId, AgentRunStatus expected, AgentRunStatus next) {
        AgentRun run = agentRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found: " + runId));
        if (run.status() != expected) {
            throw new IllegalStateException("Expected agent run status " + expected + " but was " + run.status());
        }
        run.transition(next);
        return agentRunRepository.save(run);
    }
}
