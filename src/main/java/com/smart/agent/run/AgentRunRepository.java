package com.smart.agent.run;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepository {
    default Optional<AgentRun> findById(String id) { return Optional.empty(); }

    AgentRun save(AgentRun run);

    Optional<AgentRun> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id);

    List<AgentRun> findByTenantIdAndUserIdAndConversationId(String tenantId, String userId, String conversationId);
}
