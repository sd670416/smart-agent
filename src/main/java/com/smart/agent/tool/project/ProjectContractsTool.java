package com.smart.agent.tool.project;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProjectContractsTool implements AgentTool<ProjectContractsInput, ProjectContractsResult> {
    private final ProjectBusinessClient client;

    public ProjectContractsTool(ProjectBusinessClient client) { this.client = client; }
    @Override public String key() { return "project.getContracts"; }
    @Override public Class<ProjectContractsInput> inputType() { return ProjectContractsInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "分页查询允许访问项目的五类合同摘要；contractType 可省略或使用 material、labor、machine、subcontract、other"; }
    @Override public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"projectId\":{\"type\":\"string\"},"
                + "\"contractType\":{\"type\":\"string\"},"
                + "\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"}},"
                + "\"required\":[\"projectId\"],\"additionalProperties\":false}";
    }
    @Override public ProjectContractsResult execute(ProjectContractsInput input, ToolContext context) {
        return client.getContracts(context, input);
    }
    @Override public Optional<String> projectId(ProjectContractsInput input) { return Optional.of(input.projectId()); }
}
