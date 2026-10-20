package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.profit.*;
import com.smart.agent.tool.project.ProjectQueryResult;
import com.smart.agent.clarification.PendingClarificationService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class ProjectProfitChatTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatControllerIT.InMemoryConversationRepository conversations = new ChatControllerIT.InMemoryConversationRepository();
    private final ConversationService conversationService = new ConversationService(conversations);
    private final ConversationContextService contexts = new ConversationContextService(
            new ChatControllerIT.InMemoryConversationContextRepository());
    private final ChatControllerIT.InMemoryPendingClarificationRepository pending = new ChatControllerIT.InMemoryPendingClarificationRepository();
    private final PendingClarificationService clarifications = new PendingClarificationService(pending, mapper);
    private final AgentRunService runs = new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(), conversations,
            new ChatControllerIT.InMemoryAgentRunStepRepository());
    private final AgentUserContext user = new AgentUserContext("tenant", "user", "identity", com.smart.agent.routing.ProjectProfitDomainContributor.permissions("both"), Set.of("p1"));
    private final List<ModelRequest> requests = new CopyOnWriteArrayList<>();
    private final List<ProjectProfitQueryInput> queries = new CopyOnWriteArrayList<>();
    private final String conversationId = conversationService.create("tenant", "user", "项目利润查询").id();
    private ToolExecutor executor;
    private List<Map<String, Object>> projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例",
            "projectCode", "20260708001"));

    @AfterEach void close() { if (executor != null) executor.close(); }

    private ChatOrchestrator orchestrator(ModelGateway model) {
        return orchestrator(model, new ChatControllerIT.TestBeans.TestApprovalBusinessClient());
    }

    private ChatOrchestrator orchestrator(ModelGateway model,
            com.smart.agent.tool.approval.ApprovalBusinessClient approvalClient) {
        ProjectProfitBusinessClient client = (context, input) -> {
            queries.add(input);
            if ("detail".equals(input.mode())) return new ProjectQueryResult("detail", input.page(), input.pageSize(),
                    2L, false, List.of(), List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                    Map.of("projectId", "outside", "projectName", "不可见项目", "projectCode", "002")), List.of());
            return new ProjectQueryResult("aggregate", 1, 20, 1L, false, List.of(),
                    List.of(Map.of("projectCount", queries.size(), "actualProfit", new java.math.BigDecimal("123.45"))), List.of());
        };
        var projects = new com.smart.agent.tool.project.LocalProjectBusinessClient() {
            @Override public ProjectQueryResult query(ToolContext context, com.smart.agent.tool.project.ProjectQueryInput input) {
                var rows = projectRows.stream().filter(row -> context.canAccessProject(row.get("projectId").toString()))
                        .filter(row -> input.filter() == null || matchesProjectFilter(row, input.filter())).toList();
                return new ProjectQueryResult("detail", 1, 6, (long) rows.size(), false, List.of(), rows, List.of());
            }
        };
        ToolRegistry tools = new ToolRegistry(List.of(new ProjectProfitQueryTool(client,
                new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new com.smart.agent.tool.approval.ApprovalQueryTool(approvalClient),
                new com.smart.agent.tool.project.ProjectQueryTool(projects),
                new ProjectProfitClarifyDateTool(),
                new com.smart.agent.tool.system.CurrentTimeTool(
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-12T02:00:00Z"), java.time.ZoneOffset.UTC),
                java.time.ZoneId.of("Asia/Shanghai"))));
        executor = new ToolExecutor(tools, runs);
        QueryIntentResolver routing = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new ProjectProfitDomainContributor(), new ProjectDomainContributor(),
                new MaterialRefundDomainContributor(), new ProjectReceiptsDomainContributor(),
                new ApprovalDomainContributor(), new BoardDomainContributor())));
        return new ChatOrchestrator(conversationService, runs, request -> {
            requests.add(request);
            return model.stream(request);
        }, tools, executor, mapper, null, Duration.ofSeconds(10), null, null, contexts, routing, clarifications);
    }

    private boolean hasCurrentResult(ModelRequest request) {
        return request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance);
    }


    @Test void eachFollowUpRequeriesAndModelReceivesTheResult() {
        ChatOrchestrator chat=orchestrator(request->hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("根据本轮结果给出利润金额",1,1))
                : Flux.just(new ModelEvent.ToolRequested("f","projectProfit.query","{\"metric\":\"both\",\"mode\":\"detail\"}")));
        var first=chat.stream(new ChatCommand(conversationId,"查询项目利润列表",null),user,"t1").collectList().block();
        chat.stream(new ChatCommand(conversationId,"合计多少",null),user,"t2").collectList().block();
        assertThat(queries).hasSize(2);
        assertThat(first.toString()).contains("根据本轮结果给出利润金额");
        assertThat(requests.get(0).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactlyInAnyOrder("projectProfit.query", "projectProfit.clarifyDate");
        assertThat(requests.get(0).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.REQUIRED);
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("trusted-query-context");
    }
    @Test void ordinalFollowUpCarriesOnlyCurrentScopedProjectReferences() {
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("项目利润查询完成",1,1));
            if(queries.isEmpty())return Flux.just(new ModelEvent.ToolRequested("f","projectProfit.query","{\"mode\":\"detail\"}"));
            assertThat(request.redactedConversationMessages().toString()).contains("trusted-project-profit-references","p1");
            assertThat(request.redactedConversationMessages().toString()).doesNotContain("不可见项目");
            return Flux.just(new ModelEvent.ToolRequested("f","projectProfit.query","{\"projectId\":\"p1\"}"));
        });
        chat.stream(new ChatCommand(conversationId,"查询项目利润列表",null),user,"t1").collectList().block();
        var second=chat.stream(new ChatCommand(conversationId,"看第一个项目",null),user,"t2").collectList().block();
        assertThat(second).noneMatch(e->"error".equals(e.type()));
        assertThat(queries).hasSize(2);
        assertThat(queries.get(1).projectId()).isEqualTo("p1");
    }
    private boolean matchesProjectFilter(Map<String,Object> row,com.smart.agent.tool.project.ProjectQueryFilter filter) {
        return filter.conditions().stream().allMatch(c->Objects.equals(row.get(c.field()),c.value()));
    }
    @Test void ambiguousDateAsksBeforeFetchingBusinessData() {
        ChatOrchestrator chat=orchestrator(request->Flux.just(new ModelEvent.ToolRequested("date","projectProfit.clarifyDate","{}")));
        var events=chat.stream(new ChatCommand(conversationId,"查询2026年项目利润",null),user,"t").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events.toString()).contains("clarification");
    }
    @Test void budgetProfitDoesNotDemandFinancialMenus(){
        ChatOrchestrator chat=orchestrator(request->hasCurrentResult(request)
                ?Flux.just(new ModelEvent.Completed("预算利润查询完成",1,1))
                :Flux.just(new ModelEvent.ToolRequested("p","projectProfit.query","{\"metric\":\"budget\"}")));
        var budgetOnly=new AgentUserContext("tenant","user","identity",ProjectProfitDomainContributor.permissions("budget"),Set.of("p1"));
        var events=chat.stream(new ChatCommand(conversationId,"预算利润是多少",null),budgetOnly,"t").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type()));assertThat(queries).hasSize(1);assertThat(queries.getFirst().metric()).isEqualTo("budget");
    }
    @Test void narrowProfitPermissionSurvivesMetriclessFollowUp(){
        for(String metric:List.of("budget","actual")){
            ChatOrchestrator chat=orchestrator(request->hasCurrentResult(request)
                    ?Flux.just(new ModelEvent.Completed("利润查询完成",1,1))
                    :Flux.just(new ModelEvent.ToolRequested("p","projectProfit.query","{\"metric\":\""+metric+"\"}")));
            var limited=new AgentUserContext("tenant","user","identity",ProjectProfitDomainContributor.permissions(metric),Set.of("p1"));
            int previous=queries.size();
            chat.stream(new ChatCommand(conversationId,"budget".equals(metric)?"查询预算利润":"查询实际利润",null),limited,"t1").collectList().block();
            var follow=chat.stream(new ChatCommand(conversationId,"利润率多少",null),limited,"t2").collectList().block();
            assertThat(follow).noneMatch(e->"error".equals(e.type()));assertThat(queries).hasSize(previous+2);
            executor.close();
        }
    }
    @Test void explicitMetricSwitchRechecksNewSourcesBeforeModel(){
        ChatOrchestrator chat=orchestrator(request->hasCurrentResult(request)
                ?Flux.just(new ModelEvent.Completed("预算利润查询完成",1,1))
                :Flux.just(new ModelEvent.ToolRequested("p","projectProfit.query","{\"metric\":\"budget\"}")));
        var limited=new AgentUserContext("tenant","user","identity",ProjectProfitDomainContributor.permissions("budget"),Set.of("p1"));
        chat.stream(new ChatCommand(conversationId,"查询预算利润",null),limited,"t1").collectList().block();
        int modelCalls=requests.size();
        var denied=chat.stream(new ChatCommand(conversationId,"改查实际利润",null),limited,"t2").collectList().block();
        assertThat(denied).anyMatch(e->"AGENT_TOOL_FORBIDDEN".equals(e.code()));assertThat(requests).hasSize(modelCalls);assertThat(queries).hasSize(1);
    }
    @Test void switchingFromBudgetDoesNotReuseItsDateAndProjectFilters(){
        contexts.upsert("tenant","user",conversationId,ConversationContextService.ACTIVE_BUSINESS_DOMAIN,
                "{\"domain\":\"BUDGET_COST\",\"toolKey\":\"budgetCost.query\"}","old");
        contexts.upsert("tenant","user",conversationId,"QUERY_BUDGET_COST",
                "{\"arguments\":{\"projectId\":\"p1\",\"startDate\":\"2020-01-01\"}}","old");
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("利润查询完成",1,1));
            assertThat(request.redactedConversationMessages().toString()).doesNotContain("trusted-query-context","2020-01-01");
            return Flux.just(new ModelEvent.ToolRequested("p","projectProfit.query","{}"));
        });
        var events=chat.stream(new ChatCommand(conversationId,"改查全部项目利润",null),user,"t").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type()));assertThat(queries).hasSize(1);assertThat(queries.getFirst().startDate()).isNull();
    }
    @Test void dateSelectionResumesQueryWithConfirmedDimension() {
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("查询完成",1,1));
            if(pending.findActive("tenant","user",conversationId).isPresent())throw new AssertionError("pending must be resolved first");
            if(request.redactedConversationMessages().toString().contains("用户确认的日期字段=projectCreateDate"))
                return Flux.just(new ModelEvent.ToolRequested("f","projectProfit.query",
                        "{\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
            return Flux.just(new ModelEvent.ToolRequested("date","projectProfit.clarifyDate","{}"));
        });
        chat.stream(new ChatCommand(conversationId,"查2026年1月项目利润",null),user,"t1").collectList().block();
        var confirmation=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"按项目创建日期",List.of(),null,
                new ChatCommand.ClarificationSelection(confirmation.id(),"date:projectCreateDate")),user,"t2").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type()));
        assertThat(queries).hasSize(1);assertThat(queries.getFirst().dateField()).isEqualTo("projectCreateDate");
    }
    @Test void missingOneSourcePermissionRefusesBeforeAnyModelOrQuery() {
        ChatOrchestrator chat=orchestrator(request->{throw new AssertionError("must reject before model");});
        Set<String> permissions=new HashSet<>(ProjectProfitDomainContributor.permissions("both"));
        permissions.remove("menu:finance:other-income-registration");
        var limited=new AgentUserContext("tenant","user","identity",permissions,Set.of("p1"));
        var events=chat.stream(new ChatCommand(conversationId,"查询项目利润多少",null),limited,"t").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events).anyMatch(e->"AGENT_TOOL_FORBIDDEN".equals(e.code()));
    }
    @Test void selectingAllDatesOverridesPreviouslySavedDateRange() {
        contexts.upsert("tenant","user",conversationId,ConversationContextService.ACTIVE_BUSINESS_DOMAIN,
                "{\"domain\":\"PROJECT_PROFIT\",\"toolKey\":\"projectProfit.query\"}","old");
        contexts.upsert("tenant","user",conversationId,"QUERY_PROJECT_PROFIT",
                "{\"arguments\":{\"metric\":\"both\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-31\"}}","old");
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("累计金额查询完成",1,1));
            String messages=request.redactedConversationMessages().toString();
            if(messages.contains("用户确认不限定日期")) {
                assertThat(messages).contains("删除dateField、startDate、endDate");
                return Flux.just(new ModelEvent.ToolRequested("f","projectProfit.query","{\"metric\":\"both\"}"));
            }
            return Flux.just(new ModelEvent.ToolRequested("date","projectProfit.clarifyDate","{}"));
        });
        chat.stream(new ChatCommand(conversationId,"查2026年项目利润",null),user,"t1").collectList().block();
        var confirmation=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"不限定日期",List.of(),null,
                new ChatCommand.ClarificationSelection(confirmation.id(),"date:allProjectDates")),user,"t2").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type()));
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().dateField()).isNull();assertThat(queries.getFirst().startDate()).isNull();assertThat(queries.getFirst().endDate()).isNull();
    }
}
