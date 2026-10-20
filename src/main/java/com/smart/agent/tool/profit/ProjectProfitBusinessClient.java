package com.smart.agent.tool.profit;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface ProjectProfitBusinessClient {
    ProjectQueryResult query(ToolContext context,ProjectProfitQueryInput input);
}
