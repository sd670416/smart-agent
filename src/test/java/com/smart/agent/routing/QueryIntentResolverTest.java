package com.smart.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryIntentResolverTest {
    private final BusinessDomainRegistry registry = new BusinessDomainRegistry(List.of(
            () -> new BusinessDomainDescriptor("PROJECT", "项目", List.of("project.query"), List.of(), "REALTIME",
                    List.of("项目")),
            () -> new BusinessDomainDescriptor("APPROVAL", "审批", List.of("approval.query"), List.of(), "REALTIME",
                    List.of("待办", "审批"))));
    private final QueryIntentResolver resolver = new QueryIntentResolver(registry);
    private final AgentUserContext user = new AgentUserContext("tenant", "user", "identity",
            Set.of("menu:project", "menu:board:manage"), Set.of());

    @Test
    void resolvesUniqueDomain() {
        IntentResolution result = resolver.resolve("查一下待办", Map.of(), null, user);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("APPROVAL", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void activeDomainBreaksTieOnlyWhenItIsTheStrongestContext() {
        IntentResolution result = resolver.resolve("查一下项目", Map.of(), "PROJECT", user);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("PROJECT", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void equalCandidatesRequireClarification() {
        IntentResolution result = resolver.resolve("统计项目审批", Map.of(), null, user);

        assertEquals(IntentResolution.Status.NEEDS_CLARIFICATION, result.status());
        assertTrue(result.ambiguity().isPresent());
    }

    @Test
    void resolvesASelectedDomainOnlyWhenItIsCurrentlyAvailable() {
        IntentResolution result = resolver.resolveSelectedDomain("APPROVAL", "统计项目审批", user);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("APPROVAL", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void projectRegionDistributionUsesProjectDomain() {
        QueryIntentResolver withBoard = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectDomainContributor(), new BoardDomainContributor())));

        IntentResolution chart = withBoard.resolve("我说的是项目区域分布", Map.of(), "PROJECT", user);
        IntentResolution project = withBoard.resolve("查询项目名称", Map.of(), null, user);

        assertEquals("PROJECT", chart.candidate().orElseThrow().domain().code());
        assertEquals("PROJECT", project.candidate().orElseThrow().domain().code());
    }

    @Test
    void migratedRegionalStatisticsIgnoreLegacyBoardEntryName() {
        QueryIntentResolver withBoard = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectDomainContributor(), new BoardDomainContributor())));
        for (String question : List.of("经营看板的区域统计", "项目看板的地区统计", "经营看板按省份统计项目数量")) {
            IntentResolution result = withBoard.resolve(question, Map.of(), null, user);
            assertEquals(IntentResolution.Status.RESOLVED, result.status(), question);
            assertEquals("PROJECT", result.candidate().orElseThrow().domain().code(), question);
        }
        AgentUserContext boardOnly = new AgentUserContext("tenant", "user", "identity",
                Set.of("menu:board:manage"), Set.of());
        assertEquals(IntentResolution.Status.UNKNOWN,
                withBoard.resolve("经营看板的区域统计", Map.of(), null, boardOnly).status());
    }

    @Test
    void genericProjectQueryUsesProjectDomain() {
        QueryIntentResolver withBoard = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectDomainContributor(), new BoardDomainContributor())));

        IntentResolution result = withBoard.resolve("查项目", Map.of(), null, user);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("PROJECT", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void projectRegionMetricWithoutBoardWordResolvesProject() {
        QueryIntentResolver withBoard = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectDomainContributor(), new BoardDomainContributor())));

        IntentResolution result = withBoard.resolve("各省有多少项目", Map.of(), null, user);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("PROJECT", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void projectCountWithinManageBoardUsesProjectButProfitStaysOnBoard() {
        QueryIntentResolver withBoard = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectDomainContributor(), new BoardDomainContributor())));

        IntentResolution projectCount = withBoard.resolve("经营看板的项目总数", Map.of(), null, user);
        IntentResolution profit = withBoard.resolve("经营看板的实际项目利润", Map.of(), null, user);
        IntentResolution progress = withBoard.resolve("项目看板的项目完成进度", Map.of(), null, user);
        IntentResolution contract = withBoard.resolve("各类型项目合同金额", Map.of(), null, user);

        assertEquals("PROJECT", projectCount.candidate().orElseThrow().domain().code());
        assertEquals("BOARD", profit.candidate().orElseThrow().domain().code());
        assertEquals("BOARD", progress.candidate().orElseThrow().domain().code());
        assertEquals("BOARD", contract.candidate().orElseThrow().domain().code());
    }

    @Test
    void genericProjectQuestionDoesNotOfferUnavailableBoardOption() {
        QueryIntentResolver withBoard = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectDomainContributor(), new BoardDomainContributor())));
        AgentUserContext projectOnly = new AgentUserContext("tenant", "user", "identity",
                Set.of("menu:project"), Set.of());

        IntentResolution result = withBoard.resolve("查项目", Map.of(), null, projectOnly);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("PROJECT", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void boardPermissionCannotClaimMigratedProjectCountWithoutProjectMenu() {
        QueryIntentResolver withBoard = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectDomainContributor(), new BoardDomainContributor())));
        AgentUserContext boardOnly = new AgentUserContext("tenant", "user", "identity",
                Set.of("menu:board:manage"), Set.of());

        IntentResolution result = withBoard.resolve("经营看板的项目总数", Map.of(), null, boardOnly);

        assertEquals(IntentResolution.Status.UNKNOWN, result.status());
    }
}
