package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectReimburseRoutingTest {
    private final QueryIntentResolver resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
            new ProjectReimburseDomainContributor(), new OtherPaymentRequestDomainContributor(),
            new PaymentRequestDomainContributor(), new ProjectDomainContributor(), new ApprovalDomainContributor())));
    private final AgentUserContext user = new AgentUserContext("t", "u", "i",
            Set.of("menu:project", "menu:finance:project-reimburse"), Set.of("p"));

    @Test void projectReimburseNeedsOnlyItsOwnMenuAndOverridesPreviousSource() {
        for (String q : List.of("项目报销列表", "工程报销金额", "项目报销明细"))
            assertEquals("PROJECT_REIMBURSE", resolver.resolve(q, Map.of(), "PROJECT", user).candidate().orElseThrow().domain().code());
        assertEquals("PROJECT_REIMBURSE", resolver.resolve("合计多少", Map.of(), "PROJECT_REIMBURSE", user).candidate().orElseThrow().domain().code());
        assertFalse(ProjectReimburseDomainContributor.matches("查询个人报销"));
    }

    @Test void workflowAndMissingReimbursePermissionCannotReadExpenseData() {
        assertEquals("APPROVAL", resolver.resolve("项目报销待办", Map.of(), "PROJECT_REIMBURSE", user).candidate().orElseThrow().domain().code());
        var denied = new AgentUserContext("t", "u", "i", Set.of("menu:project"), Set.of("p"));
        assertTrue(resolver.resolve("项目报销列表", Map.of(), "PROJECT", denied).candidate().isEmpty());
    }
}
