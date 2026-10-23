package com.smart.agent.tool.cost;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface CostContractBusinessClient {
    ProjectQueryResult query(ToolContext context,CostContractQueryInput input);
    default ProjectQueryResult compare(ToolContext context,CostContractCompareInput input) {
        throw new UnsupportedOperationException("Comparison not implemented");
    }
}
