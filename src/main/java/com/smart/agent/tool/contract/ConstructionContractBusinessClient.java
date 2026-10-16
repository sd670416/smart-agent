package com.smart.agent.tool.contract;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ConstructionContractBusinessClient {
    ProjectQueryResult query(ToolContext context, ConstructionContractQueryInput input);
    default ProjectQueryResult compare(ToolContext context, ConstructionContractCompareInput input) {
        throw new UnsupportedOperationException("Comparison not configured");
    }
}
