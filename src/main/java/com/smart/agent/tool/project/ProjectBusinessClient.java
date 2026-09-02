package com.smart.agent.tool.project;

import com.smart.agent.tool.ToolContext;

public interface ProjectBusinessClient {
    ProjectOverviewResult getOverview(ToolContext context, String projectId);
}
