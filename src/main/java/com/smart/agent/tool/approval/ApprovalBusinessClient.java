package com.smart.agent.tool.approval;

import com.smart.agent.tool.ToolContext;

public interface ApprovalBusinessClient {
    ApprovalQueryResult query(ToolContext context, ApprovalQueryInput input);
    ApprovalDetailResult detail(ToolContext context, ApprovalDetailInput input);
}
