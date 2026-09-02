package com.smart.agent.tool;

import java.util.Optional;

public interface AgentTool<I, O> {
    String key();

    Class<I> inputType();

    String requiredPermission();

    ToolRisk risk();

    default String description() {
        return key();
    }

    default String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
    }

    O execute(I input, ToolContext context);

    default Optional<String> projectId(I input) {
        return Optional.empty();
    }
}
