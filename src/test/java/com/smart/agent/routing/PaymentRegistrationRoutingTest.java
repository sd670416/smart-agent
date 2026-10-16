package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRegistrationRoutingTest {
    private QueryIntentResolver resolver() {
        return new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectDomainContributor(),
                new BoardDomainContributor(), new ApprovalDomainContributor(), new PaymentRegistrationDomainContributor())));
    }
    @Test void financialRegistrationUsesItsOwnDomainAndWorkflowStillUsesApproval() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:finance:payment-registration", "menu:project"), Set.of("p"));
        for (String question : List.of("查询回款登记", "查询项目回款登记金额合计"))
            assertEquals("PAYMENT_REGISTRATION", resolver().resolve(question, Map.of(), null, user).candidate().orElseThrow().domain().code());
        assertEquals("APPROVAL", resolver().resolve("回款登记待办", Map.of(), null, user).candidate().orElseThrow().domain().code());
        assertEquals("PAYMENT_REGISTRATION", resolver().resolve("合计多少", Map.of(), "PAYMENT_REGISTRATION", user).candidate().orElseThrow().domain().code());
    }
    @Test void projectOrBoardMenuCannotReadFinancialRegistration() {
        var user = new AgentUserContext("t", "u", "i", Set.of("menu:board:manage", "menu:project"), Set.of("p"));
        assertEquals(IntentResolution.Status.UNKNOWN, resolver().resolve("查询回款登记", Map.of(), null, user).status());
    }
}
