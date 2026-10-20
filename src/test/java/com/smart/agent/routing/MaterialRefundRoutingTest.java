package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialRefundRoutingTest {
    private final QueryIntentResolver resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
            new MaterialRefundDomainContributor(), new OtherPaymentRequestDomainContributor(),
            new PaymentRequestDomainContributor(), new ProjectDomainContributor(), new ApprovalDomainContributor())));
    private final AgentUserContext user = new AgentUserContext("t", "u", "i",
            Set.of("menu:project", "menu:material:refund"), Set.of("p"));

    @Test void materialRefundNeedsOnlyItsOwnMenuAndOverridesPreviousSource() {
        for (String q : List.of("材料退货退款列表", "材料退款金额", "材料退货退款明细"))
            assertEquals("MATERIAL_REFUND", resolver.resolve(q, Map.of(), "PROJECT", user).candidate().orElseThrow().domain().code());
        assertEquals("MATERIAL_REFUND", resolver.resolve("合计多少", Map.of(), "MATERIAL_REFUND", user).candidate().orElseThrow().domain().code());
        assertFalse(MaterialRefundDomainContributor.matches("查询个人报销"));
    }

    @Test void workflowAndMissingReimbursePermissionCannotReadExpenseData() {
        assertEquals("APPROVAL", resolver.resolve("材料退货退款待办", Map.of(), "MATERIAL_REFUND", user).candidate().orElseThrow().domain().code());
        var denied = new AgentUserContext("t", "u", "i", Set.of("menu:project"), Set.of("p"));
        assertTrue(resolver.resolve("材料退货退款列表", Map.of(), "PROJECT", denied).candidate().isEmpty());
    }

    @Test void approvalHandlerAndOverdueQuestionsDoNotGetLockedToRefundBusinessData() {
        var noMaterialMenu = new AgentUserContext("t", "u", "i", Set.of("menu:project"), Set.of("p"));
        for (String question : List.of("材料退货退款谁在审批", "材料退货退款超时审批")) {
            assertEquals("APPROVAL", resolver.resolve(question, Map.of(), "MATERIAL_REFUND", user)
                    .candidate().orElseThrow().domain().code());
            assertEquals("APPROVAL", resolver.resolve(question, Map.of(), "MATERIAL_REFUND", noMaterialMenu)
                    .candidate().orElseThrow().domain().code());
        }
    }
}
