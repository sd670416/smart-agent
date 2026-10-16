package com.smart.agent.tool.disbursement;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ProjectReimburseBusinessClient {
    ProjectQueryResult query(ToolContext context, ProjectReimburseQueryInput input);
}
