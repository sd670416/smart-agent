package com.smart.agent.tool.disbursement;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ProjectReimburseBusinessClient {
    default ProjectQueryResult compare(ToolContext context, ProjectReimburseCompareInput input) {
        throw new UnsupportedOperationException("Comparison not implemented");
    }
    ProjectQueryResult query(ToolContext context, ProjectReimburseQueryInput input);
}
