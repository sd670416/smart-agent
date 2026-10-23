package com.smart.agent.tool.funding;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface ProjectFundingBusinessClient {
    ProjectQueryResult query(ToolContext context,ProjectFundingQueryInput input);
    default ProjectQueryResult compare(ToolContext context,ProjectFundingCompareInput input) {
        throw new UnsupportedOperationException("Funding comparison not implemented");
    }
}
