package com.smart.agent.tool.projectrisk;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface ProjectRiskBusinessClient { ProjectQueryResult query(ToolContext context,ProjectRiskQueryInput input); }
