package com.smart.agent.routing;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApprovalDomainContributor implements BusinessDomainContributor {
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            "APPROVAL", "审批", List.of("approval.query", "approval.getDetail"),
            List.of(new DataScopeDescriptor("PROCESS_SCOPE", "流程可见范围", List.of("TODO", "DONE", "STARTED"))),
            "REALTIME", List.of("待办", "已办", "我发起", "审批", "流程"));

    @Override
    public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
}
