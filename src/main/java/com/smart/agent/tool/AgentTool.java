package com.smart.agent.tool;

import java.util.Optional;

public interface AgentTool<I, O> {
    String key();

    Class<I> inputType();

    String requiredPermission();

    ToolRisk risk();

    O execute(I input, ToolContext context);

    default Optional<String> projectId(I input) {
        return Optional.empty();
    }
}
