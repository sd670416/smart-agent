package com.smart.agent.tool.project;

import com.smart.agent.tool.ToolContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LocalProjectBusinessClient implements ProjectBusinessClient {
    @Override
    public ProjectOverviewResult getOverview(ToolContext context, String projectId) {
        if (!context.canAccessProject(projectId)) {
            throw new IllegalArgumentException("Project is outside trusted scope");
        }
        return new ProjectOverviewResult(projectId, "项目 " + projectId, "IN_PROGRESS", 0.42);
    }
}
