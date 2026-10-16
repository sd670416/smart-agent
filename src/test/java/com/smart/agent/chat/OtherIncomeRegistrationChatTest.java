package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.income.*;
import com.smart.agent.tool.project.ProjectQueryResult;
import com.smart.agent.clarification.PendingClarificationService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class OtherIncomeRegistrationChatTest {
    @Test void incomeTotalAndNextPageFollowUpsRefreshWithinTheActiveDomain() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("实时其他收入登记结果", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query",
                queries.size() == 1 ? "{\"mode\":\"summary\"}" : "{\"mode\":\"detail\",\"page\":" + (queries.size() == 2 ? 2 : 1) + "}")));
        chat.stream(new ChatCommand(conversationId, "查询其他收入登记列表", null), user, "trace1").collectList().block();
        chat.stream(new ChatCommand(conversationId, "合计多少", null), user, "trace2").collectList().block();
        chat.stream(new ChatCommand(conversationId, "下一页", null), user, "trace3").collectList().block();
        assertThat(queries).hasSize(3);
        assertThat(queries.get(1).mode()).isEqualTo("summary");
        assertThat(queries.get(2).page()).isEqualTo(2);
        assertThat(requests.get(4).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactlyInAnyOrder("otherIncomeRegistration.query", "otherIncomeRegistration.clarifyDate");
        assertThat(requests.get(4).redactedConversationMessages().toString()).contains("trusted-query-context");
    }

    @Test void incomeWorkflowRequestsForAllTypesStillUseApproval() {
        for (String question : List.of("查询其他收入登记待办", "查询其他回款已办", "查询其他回款我发起的")) {
            ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                    ? Flux.just(new ModelEvent.Completed("实时待办结果", 1, 1))
                    : Flux.just(new ModelEvent.ToolRequested("approval", "approval.query", "{}")));
            var events = chat.stream(new ChatCommand(conversationId, question, null), user, "trace").collectList().block();
            assertThat(events).anyMatch(event -> "tool_result".equals(event.type()) && "approval.query".equals(event.toolKey()));
            assertThat(queries).isEmpty();
            executor.close();
        }
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatControllerIT.InMemoryConversationRepository conversations = new ChatControllerIT.InMemoryConversationRepository();
    private final ConversationService conversationService = new ConversationService(conversations);
    private final ConversationContextService contexts = new ConversationContextService(
            new ChatControllerIT.InMemoryConversationContextRepository());
    private final ChatControllerIT.InMemoryPendingClarificationRepository pending = new ChatControllerIT.InMemoryPendingClarificationRepository();
    private final PendingClarificationService clarifications = new PendingClarificationService(pending, mapper);
    private final AgentRunService runs = new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(), conversations,
            new ChatControllerIT.InMemoryAgentRunStepRepository());
    private final AgentUserContext user = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project", "menu:finance:other-income-registration"), Set.of("p1"));
    private final List<ModelRequest> requests = new CopyOnWriteArrayList<>();
    private final List<OtherIncomeRegistrationQueryInput> queries = new CopyOnWriteArrayList<>();
    private final String conversationId = conversationService.create("tenant", "user", "其他收入登记查询").id();
    private ToolExecutor executor;
    private List<Map<String, Object>> projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例",
            "projectCode", "20260708001"));

    @AfterEach void close() { if (executor != null) executor.close(); }

    private ChatOrchestrator orchestrator(ModelGateway model) {
        return orchestrator(model, new ChatControllerIT.TestBeans.TestApprovalBusinessClient());
    }

    private ChatOrchestrator orchestrator(ModelGateway model,
            com.smart.agent.tool.approval.ApprovalBusinessClient approvalClient) {
        OtherIncomeRegistrationBusinessClient client = (context, input) -> {
            queries.add(input);
            return new ProjectQueryResult("aggregate", 1, 20, 1L, false, List.of(),
                    List.of(Map.of("registrationCount", queries.size(), "receivedAmount", 123.45)), List.of());
        };
        var projects = new com.smart.agent.tool.project.LocalProjectBusinessClient() {
            @Override public ProjectQueryResult query(ToolContext context, com.smart.agent.tool.project.ProjectQueryInput input) {
                var rows = projectRows.stream().filter(row -> context.canAccessProject(row.get("projectId").toString()))
                        .filter(row -> input.filter() == null || matchesProjectFilter(row, input.filter())).toList();
                return new ProjectQueryResult("detail", 1, 6, (long) rows.size(), false, List.of(), rows, List.of());
            }
        };
        ToolRegistry tools = new ToolRegistry(List.of(new OtherIncomeRegistrationQueryTool(client,
                new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new com.smart.agent.tool.approval.ApprovalQueryTool(approvalClient),
                new com.smart.agent.tool.project.ProjectQueryTool(projects),
                new OtherIncomeRegistrationClarifyDateTool(), new com.smart.agent.tool.system.CurrentTimeTool(
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-12T02:00:00Z"), java.time.ZoneOffset.UTC),
                java.time.ZoneId.of("Asia/Shanghai"))));
        executor = new ToolExecutor(tools, runs);
        QueryIntentResolver routing = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new OtherIncomeRegistrationDomainContributor(), new ProjectDomainContributor(),
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
                ? Flux.just(new ModelEvent.Completed("模型根据本轮其他收入登记结果回答", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("query", "otherIncomeRegistration.query", "{\"mode\":\"summary\",\"projectName\":\"示例\"}")));
        var first = chat.stream(new ChatCommand(conversationId, "查询项目的其他收入登记金额", null), user, "trace1").collectList().block();
        var second = chat.stream(new ChatCommand(conversationId, "数量有多少", null), user, "trace2").collectList().block();
        assertThat(queries).hasSize(2);
        assertThat(first.toString()).contains("模型根据本轮其他收入登记结果回答");
        assertThat(second.toString()).contains("模型根据本轮其他收入登记结果回答");
        assertThat(requests.get(0).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactlyInAnyOrder("otherIncomeRegistration.query", "otherIncomeRegistration.clarifyDate");
        assertThat(requests.get(0).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.REQUIRED);
        assertThat(requests.get(1).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.AUTO);
        assertThat(contexts.find("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN)
                .orElseThrow().payloadJson()).contains("OTHER_INCOME_REGISTRATION");
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("projectId", "p1");
    }

    @Test void historicalAnswerCannotEndTurnWithoutCurrentQuery() {
        ChatOrchestrator chat = orchestrator(request -> requests.size() == 1
                ? Flux.just(new ModelEvent.Completed("历史金额9999", 1, 1))
                : hasCurrentResult(request) ? Flux.just(new ModelEvent.Completed("实时查询后的金额", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("query", "otherIncomeRegistration.query", "{\"mode\":\"summary\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "其他收入登记总金额", null), user, "trace").collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(events.stream().filter(event -> "message_delta".equals(event.type()))
                .map(ChatEvent::text).toList()).contains("实时查询后的金额").doesNotContain("历史金额9999");
    }

    @Test void projectCodeInLegacyIdFieldIsResolvedInsteadOfReportedAsForbidden() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("精确项目的其他收入登记", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query",
                        "{\"projectId\":\"20260708001\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "看一下其他收入登记", null), user, "trace")
                .collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p1");
        assertThat(events).noneMatch(event -> "error".equals(event.type()));
    }

    @Test void projectNameDoesNotSilentlyBecomeAContainsFilter() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("精确项目的其他收入登记", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query",
                        "{\"projectName\":\"示例\"}")));
        chat.stream(new ChatCommand(conversationId, "看一下示例项目的其他收入登记", null), user, "trace")
                .collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p1");
        assertThat(queries.getFirst().projectName()).isNull();
    }

    @Test void duplicateProjectNamesRequireSelectionAndPreserveQueryFilters() {
        projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                Map.of("projectId", "p2", "projectName", "示例", "projectCode", "002"));
        var both = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project", "menu:finance:other-income-registration"), Set.of("p1", "p2"));
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("选定项目查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query",
                        "{\"projectName\":\"示例\",\"mode\":\"summary\",\"dateField\":\"receivedDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}")));
        var first = chat.stream(new ChatCommand(conversationId, "示例项目1月创建的其他收入登记金额", null), both, "trace1")
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

    @Test void projectQuerySuppliesVerifiedProjectReferenceForIncomeFollowUp() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("project", "project.query", "{}")));
        chat.stream(new ChatCommand(conversationId, "查询项目信息", null), user, "trace1").collectList().block();
        contexts.upsert("tenant", "user", conversationId, "QUERY_OTHER_INCOME_REGISTRATION",
                "{\"arguments\":{\"projectName\":\"旧项目\"}}", "old");
        var second = chat.stream(new ChatCommand(conversationId, "看一下其他收入登记", null), user, "trace2")
                .collectList().block();
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("projectId", "p1", "20260708001")
                .doesNotContain("旧项目");
    }

    @Test void returningFromApprovalDoesNotInheritOldIncomeFilters() {
        contexts.upsert("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN,
                "{\"domain\":\"APPROVAL\",\"toolKey\":\"approval.query\"}", "old");
        contexts.upsert("tenant", "user", conversationId, "QUERY_OTHER_INCOME_REGISTRATION",
                "{\"arguments\":{\"projectId\":\"old-project\",\"registrationCode\":\"old-income\"}}", "old");
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query", "{}")));
        chat.stream(new ChatCommand(conversationId, "查询其他收入登记", null), user, "trace").collectList().block();
        assertThat(requests.getFirst().redactedConversationMessages().toString())
                .doesNotContain("old-project", "old-income");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isNull();
    }

    @Test void explicitContainsKeepsDeliberateMultiProjectFilter() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query",
                        "{\"projectName\":\"示例\",\"projectNameMatch\":\"contains\"}")));
        chat.stream(new ChatCommand(conversationId, "项目名称包含示例的其他收入登记", null), user, "trace").collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isNull();
        assertThat(queries.getFirst().projectName()).isEqualTo("示例");
    }

    @Test void changedPermissionsRejectPreviouslyOfferedProject() {
        projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                Map.of("projectId", "p2", "projectName", "示例", "projectCode", "002"));
        var both = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project", "menu:finance:other-income-registration"), Set.of("p1", "p2"));
        ChatOrchestrator chat = orchestrator(request -> Flux.just(new ModelEvent.ToolRequested("income",
                "otherIncomeRegistration.query", "{\"projectName\":\"示例\"}")));
        chat.stream(new ChatCommand(conversationId, "查询示例项目其他收入登记", null), both, "trace1").collectList().block();
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
        var events = chat.stream(new ChatCommand(conversationId, "经营看板的其他收入登记总金额", null), boardOnly, "trace")
                .collectList().block();
        assertThat(events).anyMatch(event -> "AGENT_TOOL_FORBIDDEN".equals(event.code()));
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("当前账号还没有其他收入登记的查看权限，暂时无法为你查询。可以联系管理员确认并开通相关权限后再试。");
        assertThat(requests).isEmpty();
        assertThat(queries).isEmpty();
    }

    @Test void incomeScopeDenialNamesIncomesRatherThanProjects() {
        ChatOrchestrator chat = orchestrator(request -> Flux.just(new ModelEvent.ToolRequested(
                "income", "otherIncomeRegistration.query", "{\"projectId\":\"p2\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "看一下其他收入登记", null), user, "trace")
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

    @Test void incomePageScopeDenialUsesTheCurrentQuerySubject() {
        ChatOrchestrator chat = orchestrator(request -> { throw new AssertionError("must reject before model"); });
        var events = chat.stream(new ChatCommand(conversationId, "看一下其他收入登记",
                new ChatCommand.PageContext("project", "p2", null, null)), user, "trace").collectList().block();
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("当前账号暂时无法查看所查询的其他收入登记信息。可以联系管理员确认相关数据的查看范围后再试。");
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

    @Test void explicitTodoRequestSwitchesOutOfIncomeDomain() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("本轮业务查询完成", 1, 1))
                : request.allowedToolSpecifications().stream().anyMatch(tool -> tool.key().equals("approval.query"))
                ? Flux.just(new ModelEvent.ToolRequested("approval", "approval.query", "{}"))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query", "{\"mode\":\"summary\"}")));
        chat.stream(new ChatCommand(conversationId, "其他收入登记总金额", null), user, "trace1").collectList().block();
        var events = chat.stream(new ChatCommand(conversationId, "查我的待办", null), user, "trace2").collectList().block();
        assertThat(events).anyMatch(event -> "tool_result".equals(event.type()) && "approval.query".equals(event.toolKey()));
        assertThat(queries).hasSize(1);
        assertThat(requests.get(2).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactly("approval.query");
        assertThat(contexts.find("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN)
                .orElseThrow().payloadJson()).contains("APPROVAL");
    }

    @Test void relativeDatesRequestTimeBeforeIncomeQuery() {
        ChatOrchestrator chat = orchestrator(request -> {
            boolean hasTime = request.redactedConversationMessages().stream().anyMatch(entry ->
                    entry instanceof ModelRequest.ToolResultMessage result && result.toolKey().equals("system.current_time"));
            boolean hasIncome = request.redactedConversationMessages().stream().anyMatch(entry ->
                    entry instanceof ModelRequest.ToolResultMessage result && result.toolKey().equals("otherIncomeRegistration.query"));
            return hasIncome ? Flux.just(new ModelEvent.Completed("本月其他收入登记金额", 1, 1)) : !hasTime
                    ? Flux.just(new ModelEvent.ToolRequested("time", "system.current_time", "{}"))
                    : Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query",
                    "{\"mode\":\"summary\",\"dateField\":\"receivedDate\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-31\"}"));
        });
        var events = chat.stream(new ChatCommand(conversationId, "本月创建的其他收入登记金额", null), user, "trace").collectList().block();
        assertThat(events.toString()).contains("本月其他收入登记金额");
        assertThat(requests.getFirst().allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactly("system.current_time");
        assertThat(queries).hasSize(1);
    }

    @Test void ambiguousDatesPauseAndSelectedDateResumesOriginalQuery() {
        ChatOrchestrator chat = orchestrator(request -> {
            if (hasCurrentResult(request)) return Flux.just(new ModelEvent.Completed("按创建日期查询完成", 1, 1));
            if (request.redactedConversationMessages().toString().contains("用户确认的日期字段=receivedDate"))
                return Flux.just(new ModelEvent.ToolRequested("income", "otherIncomeRegistration.query",
                        "{\"mode\":\"summary\",\"dateField\":\"receivedDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
            return Flux.just(new ModelEvent.ToolRequested("clarify", "otherIncomeRegistration.clarifyDate", "{}"));
        });
        var events = chat.stream(new ChatCommand(conversationId, "2026年1月其他收入登记总金额", null), user, "trace1").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events.toString()).contains("date:receivedDate", "DATE_FIELD");
        var question = pending.findActive("tenant", "user", conversationId).orElseThrow();
        var answer = chat.stream(new ChatCommand(conversationId, "到账日期", List.of(), null,
                new ChatCommand.ClarificationSelection(question.id(), "date:receivedDate")), user, "trace2").collectList().block();
        assertThat(answer.toString()).contains("按创建日期查询完成");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().dateField()).isEqualTo("receivedDate");
    }
}
