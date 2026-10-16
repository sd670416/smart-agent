package com.smart.agent.tool.payment;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;

public interface PaymentRegistrationBusinessClient {
    ProjectQueryResult query(ToolContext context, PaymentRegistrationQueryInput input);
}
