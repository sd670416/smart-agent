package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OtherPaymentRequestRoutingTest {
    private final QueryIntentResolver resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
            new OtherPaymentRequestDomainContributor(), new PaymentRequestDomainContributor(),
            new ProjectDomainContributor(), new ApprovalDomainContributor())));
    private final AgentUserContext user = new AgentUserContext("t", "u", "i",
            Set.of("menu:project", "menu:finance:payment-request", "menu:finance:other-payment-request"), Set.of("p"));

    @Test void explicitSourceSwitchOverridesPriorDomainAndProjectTerms() {
        for (String q : List.of("项目的其他付款申请金额", "其它付款申请", "其他费用付款", "其它费用付款"))
            assertEquals("OTHER_PAYMENT_REQUEST", resolver.resolve(q, Map.of(), "PAYMENT_REQUEST", user).candidate().orElseThrow().domain().code());
        assertEquals("PAYMENT_REQUEST", resolver.resolve("查询付款申请", Map.of(), "OTHER_PAYMENT_REQUEST", user).candidate().orElseThrow().domain().code());
        assertEquals("OTHER_PAYMENT_REQUEST", resolver.resolve("合计多少", Map.of(), "OTHER_PAYMENT_REQUEST", user).candidate().orElseThrow().domain().code());
    }

    @Test void workflowAndMissingLeafPermissionNeverBecomeOrdinaryPayments() {
        assertEquals("APPROVAL", resolver.resolve("其他付款申请待办", Map.of(), "OTHER_PAYMENT_REQUEST", user).candidate().orElseThrow().domain().code());
        var denied = new AgentUserContext("t", "u", "i", Set.of("menu:project", "menu:finance:payment-request"), Set.of("p"));
        assertTrue(resolver.resolve("查询其他付款申请", Map.of(), "PAYMENT_REQUEST", denied).candidate().isEmpty());
    }

    @Test void otherPaymentAliasesNeedOnlyTheirOwnLeafPermission() {
        var otherOnly = new AgentUserContext("t", "u", "i", Set.of("menu:finance:other-payment-request"), Set.of("p"));
        for (String q : List.of("查询其它付款申请", "查询其他付款申请", "其它费用付款", "其他费用付款"))
            assertEquals("OTHER_PAYMENT_REQUEST", resolver.resolve(q, Map.of(), null, otherOnly).candidate().map(c -> c.domain().code()).orElse(null), q);
    }
}
