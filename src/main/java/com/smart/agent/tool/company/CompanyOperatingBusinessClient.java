package com.smart.agent.tool.company;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
public interface CompanyOperatingBusinessClient {
    ProjectQueryResult query(ToolContext context,CompanyOperatingQueryInput input);
}
