package com.smart.agent.run;

import java.util.Optional;

public interface AgentRunRepository {
    AgentRun save(AgentRun run);

    Optional<AgentRun> findById(String id);
}
