package com.smart.agent.tool.contract;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ConstructionContractBusinessClient {
    ProjectQueryResult query(ToolContext context, ConstructionContractQueryInput input);
}
