package com.smart.agent.tool.project;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.util.Optional;
import org.springframework.stereotype.Component;

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
        return "project:read";
    }

    @Override
    public ToolRisk risk() {
        return ToolRisk.L1;
    }

    @Override
    public ProjectOverviewResult execute(ProjectOverviewInput input, ToolContext context) {
        return projectBusinessClient.getOverview(context, input.projectId());
    }

    @Override
    public Optional<String> projectId(ProjectOverviewInput input) {
        return Optional.of(input.projectId());
    }
}
