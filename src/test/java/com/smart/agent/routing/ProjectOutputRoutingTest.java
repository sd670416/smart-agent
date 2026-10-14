package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOutputRoutingTest {
    @Test void workflowQueriesForOtherOutputTypesStayInApproval() {
        var resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectDomainContributor(),
                new BoardDomainContributor(), new ApprovalDomainContributor(), new ProjectOutputDomainContributor())));
        var user = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project"), Set.of("p1"));
        for (String question : List.of("劳务产值待办", "查询产值待办", "专包产值已办"))
            assertEquals("APPROVAL", resolver.resolve(question, Map.of(), "PROJECT_OUTPUT", user)
                    .candidate().orElseThrow().domain().code());
    }

    @Test void boardPermissionCannotReadMigratedOutputMetrics() {
        var resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectDomainContributor(),
                new BoardDomainContributor(), new ApprovalDomainContributor(), new ProjectOutputDomainContributor())));
        var boardOnly = new AgentUserContext("tenant", "user", "identity", Set.of("menu:board:manage"), Set.of("p1"));
        for (String question : List.of("经营看板的累计结算金额", "工程产值合计"))
            assertEquals(IntentResolution.Status.UNKNOWN, resolver.resolve(question, Map.of(), null, boardOnly).status());
    }

    @Test void onlyActiveEngineeringContextCanDisambiguateUnqualifiedOutput() {
        var resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectDomainContributor(),
                new BoardDomainContributor(), new ApprovalDomainContributor(), new ProjectOutputDomainContributor())));
        var user = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project"), Set.of("p1"));
        assertEquals(IntentResolution.Status.UNKNOWN, resolver.resolve("查询产值", Map.of(), "PROJECT", user).status());
        assertEquals("PROJECT_OUTPUT", resolver.resolve("产值合计呢", Map.of(), "PROJECT_OUTPUT", user)
                .candidate().orElseThrow().domain().code());
        for (String question : List.of("劳务产值合计", "专包产值合计"))
            assertEquals(IntentResolution.Status.UNKNOWN, resolver.resolve(question, Map.of(), "PROJECT_OUTPUT", user).status());
    }
    @Test void migratedOutputMustNotUseLegacyBoardDomain() {
        var resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectDomainContributor(),
                new BoardDomainContributor(), new ApprovalDomainContributor(), new ProjectOutputDomainContributor())));
        var user = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project", "menu:board:manage"), Set.of("p1"));
        for (String question : List.of("经营看板的累计结算金额", "查询项目工程产值", "工程产值合计"))
            assertEquals("PROJECT_OUTPUT", resolver.resolve(question, Map.of(), null, user)
                    .candidate().orElseThrow().domain().code(), question);
    }
}
