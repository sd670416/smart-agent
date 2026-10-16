package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRequestRoutingTest {
    @Test void otherPaymentRequestsAreNotSilentlyRoutedToOrdinaryPaymentRequests() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:finance:payment-request", "menu:project"), Set.of("p"));
        assertNotEquals("PAYMENT_REQUEST", resolver().resolve("查询其他付款申请", Map.of(),
                "PAYMENT_REQUEST", user).candidate().map(c -> c.domain().code()).orElse(null));
    }
    private QueryIntentResolver resolver() {
        return new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectDomainContributor(),
                new BoardDomainContributor(), new ApprovalDomainContributor(), new PaymentRequestDomainContributor(),
                new PaymentRegistrationDomainContributor())));
    }
    @Test void financialRegistrationUsesItsOwnDomainAndWorkflowStillUsesApproval() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:finance:payment-request", "menu:project"), Set.of("p"));
        for (String question : List.of("查询付款申请", "查询项目付款申请金额合计", "查询付款申请", "查询付款申请金额"))
            assertEquals("PAYMENT_REQUEST", resolver().resolve(question, Map.of(), null, user).candidate().orElseThrow().domain().code());
        assertEquals("APPROVAL", resolver().resolve("付款申请待办", Map.of(), null, user).candidate().orElseThrow().domain().code());
        assertEquals("PAYMENT_REQUEST", resolver().resolve("合计多少", Map.of(), "PAYMENT_REQUEST", user).candidate().orElseThrow().domain().code());
    }
    @Test void explicitQuerySwitchesBetweenTheTwoFinancialSources() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:finance:payment-registration",
                "menu:finance:payment-request", "menu:project"), Set.of("p"));
        assertEquals("PAYMENT_REQUEST", resolver().resolve("查询付款申请", Map.of(),
                "PAYMENT_REGISTRATION", user).candidate().orElseThrow().domain().code());
        assertEquals("PAYMENT_REGISTRATION", resolver().resolve("查询回款登记", Map.of(),
                "PAYMENT_REQUEST", user).candidate().orElseThrow().domain().code());
        var paymentOnly = new AgentUserContext("t", "u", "i", Set.of("menu:finance:payment-registration",
                "menu:project", "menu:board:manage"), Set.of("p"));
        assertEquals(IntentResolution.Status.UNKNOWN, resolver().resolve("查询付款申请", Map.of(),
                "PAYMENT_REGISTRATION", paymentOnly).status());
    }
    @Test void projectOrBoardMenuCannotReadFinancialRegistration() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:board:manage", "menu:project"), Set.of("p"));
        assertEquals(IntentResolution.Status.UNKNOWN, resolver().resolve("查询付款申请", Map.of(), null, user).status());
    }
}

