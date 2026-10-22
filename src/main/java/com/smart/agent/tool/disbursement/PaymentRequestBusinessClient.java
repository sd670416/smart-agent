package com.smart.agent.tool.disbursement;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface PaymentRequestBusinessClient {
    default ProjectQueryResult compare(ToolContext context, PaymentRequestCompareInput input) {
        throw new UnsupportedOperationException("Comparison unavailable");
    }
    ProjectQueryResult query(ToolContext context, PaymentRequestQueryInput input);
}
