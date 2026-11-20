package com.smart.agent.tool.profit.ranking;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ProjectProfitRankingBusinessClient {
    ProjectQueryResult query(ToolContext context, ProjectProfitRankingInput input);
}
