package com.smart.agent.tool.project;

import com.smart.agent.tool.ToolContext;

public interface ProjectBusinessClient {
    ProjectOverviewResult getOverview(ToolContext context, String projectId);
    ProjectContractsResult getContracts(ToolContext context, ProjectContractsInput input);
    AccessibleProjectsResult listAccessible(ToolContext context, AccessibleProjectsInput input);
    ProjectQueryResult query(ToolContext context, ProjectQueryInput input);
}
