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
    @Override public String description() { return "查询允许访问项目的合同信息，分为独立的施工合同和材料、劳务、机械、专业分包、其他五类分包合同；只返回项目档案中可见的已审批数据；projectId 可填写项目ID、项目编号或项目名称"; }
    @Override public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"projectId\":{\"type\":\"string\"},"
                + "\"contractType\":{\"type\":\"string\"},"
                + "\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"}},"
                + "\"required\":[\"projectId\"],\"additionalProperties\":false}";
    }
    @Override public ProjectContractsResult execute(ProjectContractsInput input, ToolContext context) {
        String projectId = resolveProjectId(input.projectId(), context);
        return client.getContracts(context, new ProjectContractsInput(
                projectId, input.contractType(), input.page(), input.pageSize()));
    }
    @Override public Optional<String> projectId(ProjectContractsInput input) { return Optional.empty(); }

    private String resolveProjectId(String identifier, ToolContext context) {
        if (context.canAccessProject(identifier)) return identifier;
        int page = 1;
        do {
            AccessibleProjectsResult projects = client.listAccessible(
                    context, new AccessibleProjectsInput(null, null, page, 20));
            for (AccessibleProjectItem item : projects.items()) {
                if (matches(identifier, item.projectId()) || matches(identifier, item.projectCode())
                        || matches(identifier, item.projectName())) return item.projectId();
            }
            if (!projects.hasNext()) break;
            page++;
        } while ((long) (page - 1) * 20 < context.projectIds().size());
        throw new com.smart.agent.common.error.AgentException("AGENT_PROJECT_NOT_FOUND",
                org.springframework.http.HttpStatus.NOT_FOUND, "No accessible project matches the identifier");
    }

    private boolean matches(String expected, String actual) {
        return actual != null && expected.equalsIgnoreCase(actual.trim());
    }
}
