package com.smart.agent.tool.material;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface MaterialRefundBusinessClient {
    ProjectQueryResult query(ToolContext context, MaterialRefundQueryInput input);
}
