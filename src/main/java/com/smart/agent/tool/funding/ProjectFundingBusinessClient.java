package com.smart.agent.tool.funding;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface ProjectFundingBusinessClient {
    ProjectQueryResult query(ToolContext context,ProjectFundingQueryInput input);
}
