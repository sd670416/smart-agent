package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectReceiptsRoutingTest {
    private final QueryIntentResolver resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
            new ProjectDomainContributor(), new BoardDomainContributor(), new ApprovalDomainContributor(),
            new PaymentRegistrationDomainContributor(), new OtherIncomeRegistrationDomainContributor(),
            new ProjectReceiptsDomainContributor())));
    private AgentUserContext user(Set<String> permissions) {
        return new AgentUserContext("t", "u", "i", permissions, Set.of("p"));
    }
    @Test void completeReceiptsUsesCombinedDomainAndExplicitSourcesCanSwitch() {
        var user = user(Set.of("menu:finance:payment-registration", "menu:finance:other-income-registration", "menu:project"));
        for (String question : List.of("已收项目款多少", "项目已收款合计", "项目回款总额", "回款登记和其他回款合计"))
            assertEquals("PROJECT_RECEIPTS", resolver.resolve(question, Map.of(), "PROJECT", user).candidate().orElseThrow().domain().code());
        assertEquals("PROJECT_RECEIPTS", resolver.resolve("数量有多少", Map.of(), "PROJECT_RECEIPTS", user).candidate().orElseThrow().domain().code());
        assertEquals("PAYMENT_REGISTRATION", resolver.resolve("查询回款登记", Map.of(), "PROJECT_RECEIPTS", user).candidate().orElseThrow().domain().code());
        assertEquals("OTHER_INCOME_REGISTRATION", resolver.resolve("查询其他回款", Map.of(), "PROJECT_RECEIPTS", user).candidate().orElseThrow().domain().code());
        assertEquals("APPROVAL", resolver.resolve("查我的待办", Map.of(), "PROJECT_RECEIPTS", user).candidate().orElseThrow().domain().code());
    }
    @Test void oneMenuOrBoardPermissionCannotFallBackToPartialOrLegacyTotal() {
        for (var permissions : List.of(Set.of("menu:finance:payment-registration", "menu:project", "menu:board:manage"),
                Set.of("menu:finance:other-income-registration", "menu:project"), Set.of("menu:board:manage")))
            assertEquals(IntentResolution.Status.UNKNOWN, resolver.resolve("已收项目款多少", Map.of(), null, user(permissions)).status());
    }
    @Test void explicitSourceWinsOverGenericReceivedAmountWords() {
        var paymentOnly = user(Set.of("menu:finance:payment-registration", "menu:project"));
        assertEquals("PAYMENT_REGISTRATION", resolver.resolve("只查回款登记的已收款金额", Map.of(),
                "PROJECT_RECEIPTS", paymentOnly).candidate().orElseThrow().domain().code());
        var incomeOnly = user(Set.of("menu:finance:other-income-registration", "menu:project"));
        assertEquals("OTHER_INCOME_REGISTRATION", resolver.resolve("其他收入登记的已收款金额", Map.of(),
                "PROJECT_RECEIPTS", incomeOnly).candidate().orElseThrow().domain().code());
    }
}
