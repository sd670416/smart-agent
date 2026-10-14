package com.smart.agent.tool.output;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ProjectOutputBusinessClient {
    ProjectQueryResult query(ToolContext context, ProjectOutputQueryInput input);
}

