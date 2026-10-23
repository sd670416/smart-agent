package com.smart.agent.tool.budget;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface BudgetCostBusinessClient {
    ProjectQueryResult query(ToolContext context,BudgetCostQueryInput input);
    default ProjectQueryResult compare(ToolContext context,BudgetCostCompareInput input) {
        throw new UnsupportedOperationException("Comparison not implemented");
    }
}
