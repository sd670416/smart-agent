package com.smart.agent.tool.project;

import com.smart.agent.tool.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "local")
public class LocalProjectBusinessClient implements ProjectBusinessClient {
    @Override
    public ProjectOverviewResult getOverview(ToolContext context, String projectId) {
        if (!context.canAccessProject(projectId)) {
            throw new IllegalArgumentException("Project is outside trusted scope");
        }
        return new ProjectOverviewResult(projectId, "项目 " + projectId, "IN_PROGRESS", 0.42);
    }

    @Override
    public ProjectContractsResult getContracts(ToolContext context, ProjectContractsInput input) {
        if (!context.canAccessProject(input.projectId())) throw new IllegalArgumentException("Project is outside trusted scope");
        return new ProjectContractsResult(input.projectId(), input.contractType(), input.page(), input.pageSize(),
                0, false, BigDecimal.ZERO, List.of());
    }
    @Override public AccessibleProjectsResult listAccessible(ToolContext context, AccessibleProjectsInput input) {
        return new AccessibleProjectsResult(input.page(), input.pageSize(), 0, false, List.of());
    }
}
