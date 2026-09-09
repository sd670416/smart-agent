package com.smart.agent.tool.project;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import org.springframework.stereotype.Component;

@Component
public class AccessibleProjectsTool implements AgentTool<AccessibleProjectsInput, AccessibleProjectsResult> {
    private final ProjectBusinessClient client;
    public AccessibleProjectsTool(ProjectBusinessClient client) { this.client = client; }
    @Override public String key() { return "project.listAccessible"; }
    @Override public Class<AccessibleProjectsInput> inputType() { return AccessibleProjectsInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "分页查询当前用户有权访问的项目概览，可按项目名称或编号关键字和项目状态筛选"; }
    @Override public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"},"
                + "\"status\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"},"
                + "\"pageSize\":{\"type\":\"integer\"}},\"additionalProperties\":false}";
    }
    @Override public AccessibleProjectsResult execute(AccessibleProjectsInput input, ToolContext context) {
        return client.listAccessible(context, input);
    }
}
