package com.smart.agent.tool;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.security.AgentUserContext;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool<?, ?>> tools;

    public ToolRegistry(Collection<? extends AgentTool<?, ?>> tools) {
        Map<String, AgentTool<?, ?>> indexedTools = new LinkedHashMap<>();
        for (AgentTool<?, ?> tool : tools) {
            if (indexedTools.putIfAbsent(tool.key(), tool) != null) {
                throw new IllegalArgumentException("Duplicate tool key: " + tool.key());
            }
        }
        this.tools = Map.copyOf(indexedTools);
    }

    public AgentTool<?, ?> require(String toolKey) {
        AgentTool<?, ?> tool = tools.get(toolKey);
        if (tool == null) {
            throw new AgentException("AGENT_TOOL_NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND, "Unknown tool");
        }
        return tool;
    }

    public List<AgentTool<?, ?>> allowedReadOnlyTools(AgentUserContext context) {
        return tools.values().stream()
                .filter(tool -> tool.risk() == ToolRisk.L0 || tool.risk() == ToolRisk.L1)
                .filter(tool -> tool.risk() == ToolRisk.L0
                        || context.permissions().contains(tool.requiredPermission()))
                .toList();
    }
}
