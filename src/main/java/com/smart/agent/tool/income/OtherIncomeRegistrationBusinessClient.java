package com.smart.agent.tool.income;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface OtherIncomeRegistrationBusinessClient {
    ProjectQueryResult query(ToolContext context, OtherIncomeRegistrationQueryInput input);
}
