package com.smart.agent.tool.material;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface MaterialPriceBusinessClient {
    ProjectQueryResult query(ToolContext context, MaterialPriceQueryInput input);
}
