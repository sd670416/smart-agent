package com.smart.agent.tool.project;

import com.smart.agent.tool.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "agent.business", name = "mode", havingValue = "local")
public class LocalProjectBusinessClient implements ProjectBusinessClient {
    @Override
    public ProjectArchiveDetailResult getArchiveDetail(ToolContext context, String projectId) {
        ProjectOverviewResult project = getOverview(context, projectId);
        return new ProjectArchiveDetailResult(project.projectId(), project.projectName(), java.util.List.of());
    }

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
        return new ProjectContractsResult(input.projectId(), "项目 " + input.projectId(), List.of(
                new ProjectArchiveDetailResult.Section("contract", "合同信息", "EMPTY",
                        java.util.Map.of(), List.of(), null),
                new ProjectArchiveDetailResult.Section("subcontract", "分包合同信息", "EMPTY",
                        java.util.Map.of(), List.of(), null)));
    }
    @Override public AccessibleProjectsResult listAccessible(ToolContext context, AccessibleProjectsInput input) {
        return new AccessibleProjectsResult(input.page(), input.pageSize(), 0, false, List.of());
    }
    @Override public ProjectQueryResult query(ToolContext context, ProjectQueryInput input) {
        return ProjectQueryResult.empty(input.page(), input.pageSize());
    }
}
