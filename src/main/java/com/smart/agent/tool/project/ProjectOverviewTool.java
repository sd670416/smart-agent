package com.smart.agent.tool.project;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

@Component
public class ProjectOverviewTool implements AgentTool<ProjectOverviewInput, ProjectOverviewResult> {
    private final ProjectBusinessClient projectBusinessClient;

    public ProjectOverviewTool(ProjectBusinessClient projectBusinessClient) {
        this.projectBusinessClient = projectBusinessClient;
    }

    @Override
    public String key() {
        return "project.getOverview";
    }

    @Override
    public Class<ProjectOverviewInput> inputType() {
        return ProjectOverviewInput.class;
    }

    @Override
    public String requiredPermission() {
        return "menu:project";
    }

    @Override
    public ToolRisk risk() {
        return ToolRisk.L1;
    }

    @Override
    public String description() {
        return "查询当前用户有权访问的单个项目概览，可使用项目ID、项目编号或项目名称";
    }

    @Override
    public String argumentsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"projectId\":{\"type\":\"string\"},"
                + "\"projectCode\":{\"type\":\"string\"},"
                + "\"projectName\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}";
    }

    @Override
    public ProjectOverviewResult execute(ProjectOverviewInput input, ToolContext context) {
        String identifier = input.identifier();
        if (context.canAccessProject(identifier)) {
            return projectBusinessClient.getOverview(context, identifier);
        }
        int page = 1;
        do {
            AccessibleProjectsResult projects = projectBusinessClient.listAccessible(
                    context, new AccessibleProjectsInput(null, null, page, 20));
            Optional<AccessibleProjectItem> match = projects.items().stream()
                    .filter(item -> equalsIdentifier(identifier, item.projectId())
                            || equalsIdentifier(identifier, item.projectCode())
                            || equalsIdentifier(identifier, item.projectName()))
                    .findFirst();
            if (match.isPresent()) {
                return projectBusinessClient.getOverview(context, match.get().projectId());
            }
            if (!projects.hasNext()) break;
            page++;
        } while ((long) (page - 1) * 20 < context.projectIds().size());
        throw new AgentException("AGENT_PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND,
                "No accessible project matches the identifier");
    }

    @Override
    public Optional<String> projectId(ProjectOverviewInput input) {
        return Optional.empty();
    }

    private boolean equalsIdentifier(String expected, String actual) {
        return actual != null && expected.equalsIgnoreCase(actual.trim());
    }
}
