package com.smart.agent.tool.profit;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface ProjectProfitBusinessClient {
    ProjectQueryResult query(ToolContext context,ProjectProfitQueryInput input);
    default ProjectQueryResult compare(ToolContext context,ProjectProfitCompareInput input) {
        throw new UnsupportedOperationException("Profit comparison not implemented");
    }
}
