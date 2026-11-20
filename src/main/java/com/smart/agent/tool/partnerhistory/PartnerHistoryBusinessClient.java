package com.smart.agent.tool.partnerhistory;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface PartnerHistoryBusinessClient {
    ProjectQueryResult query(ToolContext context, PartnerHistoryQueryInput input);
}
