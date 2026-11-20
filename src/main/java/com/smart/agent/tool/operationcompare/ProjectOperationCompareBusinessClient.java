package com.smart.agent.tool.operationcompare;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ProjectOperationCompareBusinessClient {
    ProjectQueryResult compare(ToolContext context, ProjectOperationCompareInput input);
}
