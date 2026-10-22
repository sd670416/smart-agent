package com.smart.agent.tool.disbursement;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface OtherPaymentRequestBusinessClient {
    default ProjectQueryResult compare(ToolContext context, OtherPaymentRequestCompareInput input) {
        throw new UnsupportedOperationException("Comparison unavailable");
    }
    ProjectQueryResult query(ToolContext context, OtherPaymentRequestQueryInput input);
}
