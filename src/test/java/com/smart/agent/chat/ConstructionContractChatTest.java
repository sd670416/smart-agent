package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.contract.*;
import com.smart.agent.tool.project.ProjectQueryResult;
import com.smart.agent.clarification.PendingClarificationService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class ConstructionContractChatTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatControllerIT.InMemoryConversationRepository conversations = new ChatControllerIT.InMemoryConversationRepository();
    private final ConversationService conversationService = new ConversationService(conversations);
    private final ConversationContextService contexts = new ConversationContextService(
            new ChatControllerIT.InMemoryConversationContextRepository());
    private final ChatControllerIT.InMemoryPendingClarificationRepository pending = new ChatControllerIT.InMemoryPendingClarificationRepository();
    private final PendingClarificationService clarifications = new PendingClarificationService(pending, mapper);
    private final AgentRunService runs = new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(), conversations,
            new ChatControllerIT.InMemoryAgentRunStepRepository());
    private final AgentUserContext user = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project"), Set.of("p1"));
    private final List<ModelRequest> requests = new CopyOnWriteArrayList<>();
    private final List<ConstructionContractQueryInput> queries = new CopyOnWriteArrayList<>();
    private final List<ConstructionContractCompareInput> comparisons = new CopyOnWriteArrayList<>();
    private final String conversationId = conversationService.create("tenant", "user", "合同查询").id();
    private ToolExecutor executor;
    private List<Map<String, Object>> projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例",
            "projectCode", "20260708001"));

    @AfterEach void close() { if (executor != null) executor.close(); }

    private ChatOrchestrator orchestrator(ModelGateway model) {
        return orchestrator(model, new ChatControllerIT.TestBeans.TestApprovalBusinessClient());
    }

    private ChatOrchestrator orchestrator(ModelGateway model,
            com.smart.agent.tool.approval.ApprovalBusinessClient approvalClient) {
        ConstructionContractBusinessClient client = new ConstructionContractBusinessClient() {
            @Override public ProjectQueryResult compare(ToolContext context, ConstructionContractCompareInput input) {
                comparisons.add(input);
                return new ProjectQueryResult("aggregate", 1, 2, 2L, false, List.of(),
                        List.of(Map.of("metric", "建设合同总金额", "difference", 10)), List.of());
            }
            @Override public ProjectQueryResult query(ToolContext context, ConstructionContractQueryInput input) {
            queries.add(input);
            return new ProjectQueryResult("aggregate", 1, 20, 1L, false, List.of(),
                    List.of(Map.of("contractCount", queries.size(), "finalContractAmount", 123.45)), List.of());
            }
        };
        var projects = new com.smart.agent.tool.project.LocalProjectBusinessClient() {
            @Override public ProjectQueryResult query(ToolContext context, com.smart.agent.tool.project.ProjectQueryInput input) {
                var rows = projectRows.stream().filter(row -> context.canAccessProject(row.get("projectId").toString()))
                        .filter(row -> input.filter() == null || matchesProjectFilter(row, input.filter())).toList();
                return new ProjectQueryResult("detail", 1, 6, (long) rows.size(), false, List.of(), rows, List.of());
            }
        };
        ToolRegistry tools = new ToolRegistry(List.of(new ConstructionContractQueryTool(client,
                new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new ConstructionContractCompareTool(client, new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new com.smart.agent.tool.approval.ApprovalQueryTool(approvalClient),
                new com.smart.agent.tool.project.ProjectQueryTool(projects),
                new ConstructionContractClarifyDateTool(), new com.smart.agent.tool.system.CurrentTimeTool(
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-12T02:00:00Z"), java.time.ZoneOffset.UTC),
                java.time.ZoneId.of("Asia/Shanghai"))));
        executor = new ToolExecutor(tools, runs);
        QueryIntentResolver routing = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ConstructionContractDomainContributor(), new ProjectDomainContributor(),
                new ApprovalDomainContributor(), new BoardDomainContributor())));
        return new ChatOrchestrator(conversationService, runs, request -> {
            requests.add(request);
            return model.stream(request);
        }, tools, executor, mapper, null, Duration.ofSeconds(10), null, null, contexts, routing, clarifications);
    }

    private boolean hasCurrentResult(ModelRequest request) {
        return request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance);
    }

    private boolean matchesProjectFilter(Map<String, Object> row, com.smart.agent.tool.project.ProjectQueryFilter filter) {
        var checks = new ArrayList<Boolean>();
        filter.conditions().forEach(condition -> checks.add(Objects.equals(row.get(condition.field()), condition.value())));
        filter.groups().forEach(group -> checks.add(matchesProjectFilter(row, group)));
        return "OR".equals(filter.logic()) ? checks.stream().anyMatch(Boolean::booleanValue)
                : checks.stream().allMatch(Boolean::booleanValue);
    }

    @Test void toolResultReturnsToModelAndEveryFollowUpRefreshes() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("模型根据本轮合同结果回答", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("query", "constructionContract.query", "{\"mode\":\"summary\",\"projectName\":\"示例\"}")));
        var first = chat.stream(new ChatCommand(conversationId, "查询项目的施工合同金额", null), user, "trace1").collectList().block();
        var second = chat.stream(new ChatCommand(conversationId, "数量有多少", null), user, "trace2").collectList().block();
        assertThat(queries).hasSize(2);
        assertThat(first.toString()).contains("模型根据本轮合同结果回答");
        assertThat(second.toString()).contains("模型根据本轮合同结果回答");
        assertThat(requests.get(0).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactlyInAnyOrder("constructionContract.query", "constructionContract.clarifyDate", "constructionContract.compare");
        assertThat(requests.get(0).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.REQUIRED);
        assertThat(requests.get(1).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.AUTO);
        assertThat(contexts.find("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN)
                .orElseThrow().payloadJson()).contains("CONSTRUCTION_CONTRACT");
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("projectId", "p1");
    }

    @Test void comparisonReturnsToModelAndRefreshesEveryTurn() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("实时合同比较完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("compare", "constructionContract.compare",
                "{\"comparisonType\":\"YOY\",\"periodUnit\":\"MONTH\",\"current\":{\"projectName\":\"示例\",\"dateField\":\"signingDate\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}}")));
        var first = chat.stream(new ChatCommand(conversationId, "9月签订的施工合同同比", null), user, "trace1").collectList().block();
        var second = chat.stream(new ChatCommand(conversationId, "再查一次", null), user, "trace2").collectList().block();
        assertThat(first.toString()).contains("实时合同比较完成");
        assertThat(second.toString()).contains("实时合同比较完成");
        assertThat(comparisons).hasSize(2);
        assertThat(queries).isEmpty();
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("p1", "current", "2026-09-30");
    }

    @Test void comparisonPeriodSelectionRequiresTimeBeforeResuming() {
        ChatOrchestrator chat = orchestrator(request -> {
            if (request.allowedToolSpecifications().size() == 1
                    && request.allowedToolSpecifications().getFirst().key().equals("system.current_time")) {
                assertThat(request.toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.REQUIRED);
                return Flux.just(new ModelEvent.ToolRequested("time", "system.current_time", "{}"));
            }
            if (request.redactedConversationMessages().stream().anyMatch(entry ->
                    entry instanceof ModelRequest.ToolResultMessage result && result.toolKey().equals("constructionContract.compare")))
                return Flux.just(new ModelEvent.Completed("本月合同比较完成", 1, 1));
            if (request.redactedConversationMessages().toString().contains("用户确认比较期间"))
                return Flux.just(new ModelEvent.ToolRequested("compare", "constructionContract.compare",
                    "{\"comparisonType\":\"YOY\",\"periodUnit\":\"MONTH\",\"current\":{\"dateField\":\"signingDate\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-12\"}}"));
            return Flux.just(new ModelEvent.ToolRequested("clarify", "constructionContract.clarifyDate", "{\"ambiguity\":\"PERIOD\"}"));
        });
        var first = chat.stream(new ChatCommand(conversationId, "按签订日期比较施工合同同比", null), user, "t1").collectList().block();
        assertThat(first.toString()).contains("COMPARISON_PERIOD");
        assertThat(comparisons).isEmpty();
        var question = pending.findActive("tenant", "user", conversationId).orElseThrow();
        var second = chat.stream(new ChatCommand(conversationId, "本月", List.of(), null,
                new ChatCommand.ClarificationSelection(question.id(), "period:MONTH")), user, "t2").collectList().block();
        assertThat(second.toString()).contains("本月合同比较完成");
        assertThat(comparisons).hasSize(1);
    }

    @Test void comparisonProjectSelectionPreservesOtherGroupAndContractFilters() {
        projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                Map.of("projectId", "p2", "projectName", "示例", "projectCode", "002"));
        var both = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project"), Set.of("p1", "p2"));
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("选定项目比较完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("compare", "constructionContract.compare",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectName\":\"示例\",\"contractName\":\"主体\",\"dateField\":\"signingDate\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"},\"baseline\":{\"projectId\":\"p1\",\"dateField\":\"signingDate\",\"startDate\":\"2025-09-01\",\"endDate\":\"2025-09-30\"}}")));
        var first = chat.stream(new ChatCommand(conversationId, "比较示例项目施工合同和另一组", null), both, "t1").collectList().block();
        assertThat(first.toString()).contains("COMPARISON_PROJECT_REFERENCE", "current:project:p2");
        assertThat(comparisons).isEmpty();
        var question = pending.findActive("tenant", "user", conversationId).orElseThrow();
        chat.stream(new ChatCommand(conversationId, "选择第二个", List.of(), null,
                new ChatCommand.ClarificationSelection(question.id(), "current:project:p2")), both, "t2").collectList().block();
        assertThat(comparisons).hasSize(1);
        assertThat(comparisons.getFirst().current().projectId()).isEqualTo("p2");
        assertThat(comparisons.getFirst().current().contractName()).isEqualTo("主体");
        assertThat(comparisons.getFirst().baseline().projectId()).isEqualTo("p1");
        assertThat(comparisons.getFirst().baseline().startDate()).isEqualTo("2025-09-01");
    }

    @Test void historicalAnswerCannotEndTurnWithoutCurrentQuery() {
        ChatOrchestrator chat = orchestrator(request -> requests.size() == 1
                ? Flux.just(new ModelEvent.Completed("历史金额9999", 1, 1))
                : hasCurrentResult(request) ? Flux.just(new ModelEvent.Completed("实时查询后的金额", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("query", "constructionContract.query", "{\"mode\":\"summary\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "建设合同总金额", null), user, "trace").collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(events.stream().filter(event -> "message_delta".equals(event.type()))
                .map(ChatEvent::text).toList()).contains("实时查询后的金额").doesNotContain("历史金额9999");
    }

    @Test void projectCodeInLegacyIdFieldIsResolvedInsteadOfReportedAsForbidden() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("精确项目的施工合同", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query",
                        "{\"projectId\":\"20260708001\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "看一下施工合同", null), user, "trace")
                .collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p1");
        assertThat(events).noneMatch(event -> "error".equals(event.type()));
    }

    @Test void projectNameDoesNotSilentlyBecomeAContainsFilter() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("精确项目的施工合同", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query",
                        "{\"projectName\":\"示例\"}")));
        chat.stream(new ChatCommand(conversationId, "看一下示例项目的施工合同", null), user, "trace")
                .collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p1");
        assertThat(queries.getFirst().projectName()).isNull();
    }

    @Test void duplicateProjectNamesRequireSelectionAndPreserveQueryFilters() {
        projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                Map.of("projectId", "p2", "projectName", "示例", "projectCode", "002"));
        var both = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project"), Set.of("p1", "p2"));
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("选定项目查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query",
                        "{\"projectName\":\"示例\",\"mode\":\"summary\",\"dateField\":\"signingDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}")));
        var first = chat.stream(new ChatCommand(conversationId, "示例项目1月签订的施工合同金额", null), both, "trace1")
                .collectList().block();
        assertThat(first.toString()).contains("PROJECT_REFERENCE", "project:p2");
        assertThat(queries).isEmpty();
        var pendingQuestion = pending.findActive("tenant", "user", conversationId).orElseThrow();
        var second = chat.stream(new ChatCommand(conversationId, "选择第二个", List.of(), null,
                new ChatCommand.ClarificationSelection(pendingQuestion.id(), "project:p2")), both, "trace2")
                .collectList().block();
        assertThat(second.toString()).contains("选定项目查询完成");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p2");
        assertThat(queries.getFirst().projectName()).isNull();
        assertThat(queries.getFirst().startDate()).isEqualTo("2026-01-01");
        assertThat(queries.getFirst().mode()).isEqualTo("summary");
    }

    @Test void projectQuerySuppliesVerifiedProjectReferenceForContractFollowUp() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("project", "project.query", "{}")));
        chat.stream(new ChatCommand(conversationId, "查询项目信息", null), user, "trace1").collectList().block();
        contexts.upsert("tenant", "user", conversationId, "QUERY_CONSTRUCTION_CONTRACT",
                "{\"arguments\":{\"projectName\":\"旧项目\"}}", "old");
        var second = chat.stream(new ChatCommand(conversationId, "看一下施工合同", null), user, "trace2")
                .collectList().block();
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("projectId", "p1", "20260708001")
                .doesNotContain("旧项目");
    }

    @Test void returningFromApprovalDoesNotInheritOldContractFilters() {
        contexts.upsert("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN,
                "{\"domain\":\"APPROVAL\",\"toolKey\":\"approval.query\"}", "old");
        contexts.upsert("tenant", "user", conversationId, "QUERY_CONSTRUCTION_CONTRACT",
                "{\"arguments\":{\"projectId\":\"old-project\",\"contractName\":\"old-contract\"}}", "old");
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query", "{}")));
        chat.stream(new ChatCommand(conversationId, "查询施工合同", null), user, "trace").collectList().block();
        assertThat(requests.getFirst().redactedConversationMessages().toString())
                .doesNotContain("old-project", "old-contract");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isNull();
    }

    @Test void explicitContainsKeepsDeliberateMultiProjectFilter() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query",
                        "{\"projectName\":\"示例\",\"projectNameMatch\":\"contains\"}")));
        chat.stream(new ChatCommand(conversationId, "项目名称包含示例的施工合同", null), user, "trace").collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isNull();
        assertThat(queries.getFirst().projectName()).isEqualTo("示例");
    }

    @Test void changedPermissionsRejectPreviouslyOfferedProject() {
        projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                Map.of("projectId", "p2", "projectName", "示例", "projectCode", "002"));
        var both = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project"), Set.of("p1", "p2"));
        ChatOrchestrator chat = orchestrator(request -> Flux.just(new ModelEvent.ToolRequested("contract",
                "constructionContract.query", "{\"projectName\":\"示例\"}")));
        chat.stream(new ChatCommand(conversationId, "查询示例项目施工合同", null), both, "trace1").collectList().block();
        var pendingQuestion = pending.findActive("tenant", "user", conversationId).orElseThrow();
        var events = chat.stream(new ChatCommand(conversationId, "选择第二个", List.of(), null,
                new ChatCommand.ClarificationSelection(pendingQuestion.id(), "project:p2")), user, "trace2")
                .collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events).anyMatch(event -> "AGENT_TOOL_PROJECT_FORBIDDEN".equals(event.code()));
    }

    @Test void missingProjectPermissionCannotFallBackToLegacyBoardQuery() {
        ChatOrchestrator chat = orchestrator(request -> { throw new AssertionError("must reject before model"); });
        AgentUserContext boardOnly = new AgentUserContext("tenant", "user", "identity", Set.of("menu:board:manage"), Set.of("p1"));
        var events = chat.stream(new ChatCommand(conversationId, "经营看板的建设合同总金额", null), boardOnly, "trace")
                .collectList().block();
        assertThat(events).anyMatch(event -> "AGENT_PROJECT_MENU_FORBIDDEN".equals(event.code()));
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("当前账号还没有施工合同的查看权限，暂时无法为你查询。可以联系管理员确认并开通相关权限后再试。");
        assertThat(requests).isEmpty();
        assertThat(queries).isEmpty();
    }

    @Test void contractScopeDenialNamesContractsRatherThanProjects() {
        ChatOrchestrator chat = orchestrator(request -> Flux.just(new ModelEvent.ToolRequested(
                "contract", "constructionContract.query", "{\"projectId\":\"p2\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "看一下施工合同", null), user, "trace")
                .collectList().block();
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("未找到当前账号可查询的匹配项目。请确认项目编号或名称后再试。");
        assertThat(queries).isEmpty();
        assertThat(conversationService.find("tenant", "user", conversationId).messages())
                .extracting(com.smart.agent.conversation.Message::content)
                .contains("未找到当前账号可查询的匹配项目。请确认项目编号或名称后再试。");
    }

    @Test void projectScopeDenialKeepsProjectSubject() {
        ChatOrchestrator chat = orchestrator(request -> { throw new AssertionError("must reject before model"); });
        var events = chat.stream(new ChatCommand(conversationId, "查询项目信息",
                new ChatCommand.PageContext("project", "p2", null, null)), user, "trace")
                .collectList().block();
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("当前账号暂时无法查看所查询的项目信息。可以联系管理员确认相关数据的查看范围后再试。");
    }

    @Test void contractPageScopeDenialUsesTheCurrentQuerySubject() {
        ChatOrchestrator chat = orchestrator(request -> { throw new AssertionError("must reject before model"); });
        var events = chat.stream(new ChatCommand(conversationId, "看一下施工合同",
                new ChatCommand.PageContext("project", "p2", null, null)), user, "trace").collectList().block();
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("当前账号暂时无法查看所查询的施工合同信息。可以联系管理员确认相关数据的查看范围后再试。");
        assertThat(requests).isEmpty();
    }

    @Test void approvalPermissionDenialUsesTheRequestedScope() {
        for (var entry : Map.of("TODO", "待办", "PROCESSED", "已办", "STARTED", "我发起的流程").entrySet()) {
            ChatOrchestrator chat = orchestrator(request -> Flux.just(new ModelEvent.ToolRequested(
                    "approval", "approval.query", "{\"scope\":\"" + entry.getKey() + "\"}")),
                    new com.smart.agent.tool.approval.ApprovalBusinessClient() {
                        @Override public com.smart.agent.tool.approval.ApprovalQueryResult query(ToolContext context,
                                com.smart.agent.tool.approval.ApprovalQueryInput input) {
                            throw new com.smart.agent.common.error.AgentException("AGENT_APPROVAL_PERMISSION_DENIED",
                                    org.springframework.http.HttpStatus.FORBIDDEN, "denied");
                        }
                        @Override public com.smart.agent.tool.approval.ApprovalDetailResult detail(ToolContext context,
                                com.smart.agent.tool.approval.ApprovalDetailInput input) {
                            throw new AssertionError("detail should not be called");
                        }
                    });
            var events = chat.stream(new ChatCommand(conversationId, "查一下" + entry.getValue(), null), user, "trace")
                    .collectList().block();
            assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                    .containsExactly("当前账号还没有" + entry.getValue()
                            + "的查看权限，暂时无法为你查询。可以联系管理员确认并开通相关权限后再试。");
            executor.close();
        }
    }

    @Test void explicitTodoRequestSwitchesOutOfContractDomain() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("本轮业务查询完成", 1, 1))
                : request.allowedToolSpecifications().stream().anyMatch(tool -> tool.key().equals("approval.query"))
                ? Flux.just(new ModelEvent.ToolRequested("approval", "approval.query", "{}"))
                : Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query", "{\"mode\":\"summary\"}")));
        chat.stream(new ChatCommand(conversationId, "施工合同总金额", null), user, "trace1").collectList().block();
        var events = chat.stream(new ChatCommand(conversationId, "查我的待办", null), user, "trace2").collectList().block();
        assertThat(events).anyMatch(event -> "tool_result".equals(event.type()) && "approval.query".equals(event.toolKey()));
        assertThat(queries).hasSize(1);
        assertThat(requests.get(2).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactly("approval.query");
        assertThat(contexts.find("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN)
                .orElseThrow().payloadJson()).contains("APPROVAL");
    }

    @Test void relativeDatesRequestTimeBeforeContractQuery() {
        ChatOrchestrator chat = orchestrator(request -> {
            boolean hasTime = request.redactedConversationMessages().stream().anyMatch(entry ->
                    entry instanceof ModelRequest.ToolResultMessage result && result.toolKey().equals("system.current_time"));
            boolean hasContract = request.redactedConversationMessages().stream().anyMatch(entry ->
                    entry instanceof ModelRequest.ToolResultMessage result && result.toolKey().equals("constructionContract.query"));
            return hasContract ? Flux.just(new ModelEvent.Completed("本月合同金额", 1, 1)) : !hasTime
                    ? Flux.just(new ModelEvent.ToolRequested("time", "system.current_time", "{}"))
                    : Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query",
                    "{\"mode\":\"summary\",\"dateField\":\"signingDate\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-31\"}"));
        });
        var events = chat.stream(new ChatCommand(conversationId, "本月签订的施工合同金额", null), user, "trace").collectList().block();
        assertThat(events.toString()).contains("本月合同金额");
        assertThat(requests.getFirst().allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactly("system.current_time");
        assertThat(queries).hasSize(1);
    }

    @Test void ambiguousDatesPauseAndSelectedDateResumesOriginalQuery() {
        ChatOrchestrator chat = orchestrator(request -> {
            if (hasCurrentResult(request)) return Flux.just(new ModelEvent.Completed("按签订日期查询完成", 1, 1));
            if (request.redactedConversationMessages().toString().contains("用户确认的日期字段=signingDate"))
                return Flux.just(new ModelEvent.ToolRequested("contract", "constructionContract.query",
                        "{\"mode\":\"summary\",\"dateField\":\"signingDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
            return Flux.just(new ModelEvent.ToolRequested("clarify", "constructionContract.clarifyDate", "{}"));
        });
        var events = chat.stream(new ChatCommand(conversationId, "2026年1月施工合同总金额", null), user, "trace1").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events.toString()).contains("date:signingDate", "DATE_FIELD");
        var question = pending.findActive("tenant", "user", conversationId).orElseThrow();
        var answer = chat.stream(new ChatCommand(conversationId, "合同签订日期", List.of(), null,
                new ChatCommand.ClarificationSelection(question.id(), "date:signingDate")), user, "trace2").collectList().block();
        assertThat(answer.toString()).contains("按签订日期查询完成");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().dateField()).isEqualTo("signingDate");
    }
}
