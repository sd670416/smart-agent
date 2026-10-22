package com.smart.agent.tool.material;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface MaterialRefundBusinessClient {
    default ProjectQueryResult compare(ToolContext context, MaterialRefundCompareInput input) {
        throw new UnsupportedOperationException("Comparison not implemented");
    }
    ProjectQueryResult query(ToolContext context, MaterialRefundQueryInput input);
}
