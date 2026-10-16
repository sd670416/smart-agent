package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OtherIncomeRegistrationRoutingTest {
    private QueryIntentResolver resolver() {
        return new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectDomainContributor(),
                new BoardDomainContributor(), new ApprovalDomainContributor(), new OtherIncomeRegistrationDomainContributor(),
                new PaymentRegistrationDomainContributor())));
    }
    @Test void financialRegistrationUsesItsOwnDomainAndWorkflowStillUsesApproval() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:finance:other-income-registration", "menu:project"), Set.of("p"));
        for (String question : List.of("查询其他收入登记", "查询项目其他收入登记金额合计", "查询其他回款", "查询其他回款登记金额"))
            assertEquals("OTHER_INCOME_REGISTRATION", resolver().resolve(question, Map.of(), null, user).candidate().orElseThrow().domain().code());
        assertEquals("APPROVAL", resolver().resolve("其他收入登记待办", Map.of(), null, user).candidate().orElseThrow().domain().code());
        assertEquals("OTHER_INCOME_REGISTRATION", resolver().resolve("合计多少", Map.of(), "OTHER_INCOME_REGISTRATION", user).candidate().orElseThrow().domain().code());
    }
    @Test void explicitQuerySwitchesBetweenTheTwoFinancialSources() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:finance:payment-registration",
                "menu:finance:other-income-registration", "menu:project"), Set.of("p"));
        assertEquals("OTHER_INCOME_REGISTRATION", resolver().resolve("查询其他回款", Map.of(),
                "PAYMENT_REGISTRATION", user).candidate().orElseThrow().domain().code());
        assertEquals("PAYMENT_REGISTRATION", resolver().resolve("查询回款登记", Map.of(),
                "OTHER_INCOME_REGISTRATION", user).candidate().orElseThrow().domain().code());
        var paymentOnly = new AgentUserContext("t", "u", "i", Set.of("menu:finance:payment-registration",
                "menu:project", "menu:board:manage"), Set.of("p"));
        assertEquals(IntentResolution.Status.UNKNOWN, resolver().resolve("查询其他回款登记", Map.of(),
                "PAYMENT_REGISTRATION", paymentOnly).status());
    }
    @Test void projectOrBoardMenuCannotReadFinancialRegistration() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:board:manage", "menu:project"), Set.of("p"));
        assertEquals(IntentResolution.Status.UNKNOWN, resolver().resolve("查询其他收入登记", Map.of(), null, user).status());
    }
}
