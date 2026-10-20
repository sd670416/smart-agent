package com.smart.agent.tool.invoice;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface InvoiceApplicationBusinessClient {
    ProjectQueryResult query(ToolContext context, InvoiceApplicationQueryInput input);
}
