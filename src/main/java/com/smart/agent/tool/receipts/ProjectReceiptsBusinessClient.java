package com.smart.agent.tool.receipts;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface ProjectReceiptsBusinessClient {
    ProjectQueryResult query(ToolContext context, ProjectReceiptsQueryInput input);
    default ProjectQueryResult compare(ToolContext context, ProjectReceiptsCompareInput input) {
        throw new com.smart.agent.common.error.AgentException("AGENT_QUERY_UNAVAILABLE",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "已收项目款比较需要配置业务接口");
    }
}
