package com.smart.agent.tool.operation;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ProjectOperationBusinessClient {
    ProjectQueryResult query(ToolContext context, ProjectOperationQueryInput input);
}
