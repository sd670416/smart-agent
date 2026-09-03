package com.smart.agent.run;

import java.util.List;

public interface AgentRunStepRepository {
    AgentRunStep save(AgentRunStep step);
    List<AgentRunStep> findByTenantIdAndUserIdAndRunIdOrderBySequence(
            String tenantId, String userId, String runId);
}
