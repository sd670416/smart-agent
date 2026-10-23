package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.budget.*;
import com.smart.agent.tool.project.ProjectQueryResult;
import com.smart.agent.clarification.PendingClarificationService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class BudgetCostChatTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatControllerIT.InMemoryConversationRepository conversations = new ChatControllerIT.InMemoryConversationRepository();
    private final ConversationService conversationService = new ConversationService(conversations);
    private final ConversationContextService contexts = new ConversationContextService(
            new ChatControllerIT.InMemoryConversationContextRepository());
    private final ChatControllerIT.InMemoryPendingClarificationRepository pending = new ChatControllerIT.InMemoryPendingClarificationRepository();
    private final PendingClarificationService clarifications = new PendingClarificationService(pending, mapper);
    private final AgentRunService runs = new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(), conversations,
            new ChatControllerIT.InMemoryAgentRunStepRepository());
    private final AgentUserContext user = new AgentUserContext("tenant", "user", "identity", Set.of("menu:budget:cost"), Set.of("p1"));
    private final List<ModelRequest> requests = new CopyOnWriteArrayList<>();
    private final List<BudgetCostQueryInput> queries = new CopyOnWriteArrayList<>();
    private final List<BudgetCostCompareInput> comparisons = new CopyOnWriteArrayList<>();
    private final String conversationId = conversationService.create("tenant", "user", "预算成本查询").id();
    private ToolExecutor executor;
    private List<Map<String, Object>> projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例",
            "projectCode", "20260708001"));

    @AfterEach void close() { if (executor != null) executor.close(); }

    private ChatOrchestrator orchestrator(ModelGateway model) {
        return orchestrator(model, new ChatControllerIT.TestBeans.TestApprovalBusinessClient());
    }

    private ChatOrchestrator orchestrator(ModelGateway model,
            com.smart.agent.tool.approval.ApprovalBusinessClient approvalClient) {
        BudgetCostBusinessClient client = new BudgetCostBusinessClient() {
            @Override public ProjectQueryResult compare(ToolContext context,BudgetCostCompareInput input) {
                comparisons.add(input);
                return new ProjectQueryResult("aggregate",1,2,2L,false,List.of(),
                        List.of(Map.of("metric","预算成本（元）","currentValue",comparisons.size(),"baselineValue",1)),List.of());
            }
            @Override public ProjectQueryResult query(ToolContext context,BudgetCostQueryInput input) {
            queries.add(input);
            if("detail".equals(input.mode()) && input.projectId()!=null)
                return new ProjectQueryResult("detail",input.page(),input.pageSize(),1L,false,List.of(),
                        List.of(Map.of("projectId",input.projectId(),"projectName","所选项目","projectCode","002")),List.of());
            if ("detail".equals(input.mode())) return new ProjectQueryResult("detail", input.page(), input.pageSize(),
                    2L, false, List.of(), List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                    Map.of("projectId", "outside", "projectName", "不可见项目", "projectCode", "002")), List.of());
            return new ProjectQueryResult("aggregate", 1, 20, 1L, false, List.of(),
                    List.of(Map.of("projectCount", queries.size(), "budgetAmount", new java.math.BigDecimal("123.45"))), List.of());
            }
        };
        var projects = new com.smart.agent.tool.project.LocalProjectBusinessClient() {
            @Override public ProjectQueryResult query(ToolContext context, com.smart.agent.tool.project.ProjectQueryInput input) {
                var rows = projectRows.stream().filter(row -> context.canAccessProject(row.get("projectId").toString()))
                        .filter(row -> input.filter() == null || matchesProjectFilter(row, input.filter())).toList();
                return new ProjectQueryResult("detail", 1, 6, (long) rows.size(), false, List.of(), rows, List.of());
            }
        };
        ToolRegistry tools = new ToolRegistry(List.of(new BudgetCostQueryTool(client,
                new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new BudgetCostCompareTool(client,new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new com.smart.agent.tool.approval.ApprovalQueryTool(approvalClient),
                new com.smart.agent.tool.project.ProjectQueryTool(projects),
                new BudgetCostClarifyDateTool(),
                new com.smart.agent.tool.system.CurrentTimeTool(
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-12T02:00:00Z"), java.time.ZoneOffset.UTC),
                java.time.ZoneId.of("Asia/Shanghai"))));
        executor = new ToolExecutor(tools, runs);
        QueryIntentResolver routing = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new BudgetCostDomainContributor(), new ProjectDomainContributor(),
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

    @Test void comparisonRefreshesThenSelectedGroupListPaginationAndItemsKeepFilters() {
        var scoped=new AgentUserContext("tenant","user","identity",Set.of("menu:budget:cost"),Set.of("p1","p2"));
        java.util.concurrent.atomic.AtomicInteger turns=new java.util.concurrent.atomic.AtomicInteger();
        String comparison="{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectId\":\"p1\",\"dateField\":\"projectCreateDate\"},\"baseline\":{\"projectId\":\"p2\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}}";
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("根据本轮数据完成预算比较或明细查询",1,1));
            int turn=turns.incrementAndGet();
            if(turn==2 || turn==3)assertThat(request.redactedConversationMessages().toString())
                    .contains("trusted-budget-cost-comparison","p2","2026-01-31");
            if(turn<=2)return Flux.just(new ModelEvent.ToolRequested("c","budgetCost.compare",comparison));
            if(turn>3)assertThat(request.redactedConversationMessages().toString()).contains("trusted-query-context","p2","2026-01-31");
            return Flux.just(new ModelEvent.ToolRequested("q","budgetCost.query",
                    "{\"projectId\":\"p2\",\"mode\":\""+(turn==5?"items":"detail")+"\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\",\"page\":"+(turn==4?2:1)+",\"pageSize\":1}"));
        });
        for(String question:List.of("比较两个项目当前预算成本","重新比较","查看第二组项目预算列表","下一页","看该项目预算明细")) {
            var events=chat.stream(new ChatCommand(conversationId,question,null),scoped,UUID.randomUUID().toString()).collectList().block();
            assertThat(events.toString()).contains("根据本轮数据完成预算比较或明细查询");
            assertThat(events).noneMatch(e->"error".equals(e.type()));
        }
        assertThat(comparisons).hasSize(2);assertThat(queries).hasSize(3);
        assertThat(queries).allSatisfy(q->{assertThat(q.projectId()).isEqualTo("p2");assertThat(q.endDate()).isEqualTo("2026-01-31");});
        assertThat(queries.get(1).page()).isEqualTo(2);assertThat(queries.get(2).mode()).isEqualTo("items");
    }
    @Test void duplicateComparisonProjectConfirmationPreservesOtherGroupAndIgnoresModelReplacement() {
        projectRows=List.of(Map.of("projectId","p1","projectName","同名","projectCode","001"),
                Map.of("projectId","p2","projectName","同名","projectCode","002"));
        var scoped=new AgentUserContext("tenant","user","identity",Set.of("menu:budget:cost"),Set.of("p1","p2"));
        java.util.concurrent.atomic.AtomicInteger turns=new java.util.concurrent.atomic.AtomicInteger();
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("比较完成",1,1));
            return Flux.just(new ModelEvent.ToolRequested("c","budgetCost.compare",turns.incrementAndGet()==1
                    ?"{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectName\":\"同名\",\"dateField\":\"projectCreateDate\"},\"baseline\":{\"projectId\":\"p1\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}}":"{}"));
        });
        var first=chat.stream(new ChatCommand(conversationId,"比较两组预算成本",null),scoped,"a").collectList().block();
        assertThat(first.toString()).contains("COMPARISON_PROJECT_REFERENCE");assertThat(comparisons).isEmpty();
        var confirmation=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"选择第二个项目",List.of(),null,
                new ChatCommand.ClarificationSelection(confirmation.id(),"current:project:p2")),scoped,"b").collectList().block();
        assertThat(events.toString()).contains("比较完成");assertThat(comparisons).hasSize(1);
        assertThat(comparisons.getFirst().current().projectId()).isEqualTo("p2");
        assertThat(comparisons.getFirst().baseline().projectId()).isEqualTo("p1");
        assertThat(comparisons.getFirst().baseline().endDate()).isEqualTo("2026-01-31");
    }
    @Test void dateSelectionClearsBothGroupsEvenIfModelResendsHistoricalDates() {
        java.util.concurrent.atomic.AtomicInteger turns=new java.util.concurrent.atomic.AtomicInteger();
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("当前预算比较完成",1,1));
            if(turns.incrementAndGet()==1)return Flux.just(new ModelEvent.ToolRequested("d","budgetCost.clarifyDate","{}"));
            return Flux.just(new ModelEvent.ToolRequested("c","budgetCost.compare",
                    "{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectId\":\"p1\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"},\"baseline\":{\"projectId\":\"p1\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-31\"}}"));
        });
        chat.stream(new ChatCommand(conversationId,"比较项目预算成本",null),user,"a").collectList().block();
        assertThat(comparisons).isEmpty();
        var confirmation=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"不限定日期",List.of(),null,
                new ChatCommand.ClarificationSelection(confirmation.id(),"date:allProjectDates")),user,"b").collectList().block();
        assertThat(events.toString()).contains("当前预算比较完成");assertThat(comparisons).hasSize(1);
        for(var group:List.of(comparisons.getFirst().current(),comparisons.getFirst().baseline())) {
            assertThat(group.dateField()).isNull();assertThat(group.startDate()).isNull();assertThat(group.endDate()).isNull();
        }
    }


    @Test void eachFollowUpRequeriesAndModelReceivesTheResult() {
        ChatOrchestrator chat=orchestrator(request->hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("根据本轮结果给出预算金额",1,1))
                : Flux.just(new ModelEvent.ToolRequested("f","budgetCost.query","{\"mode\":\"detail\"}")));
        var first=chat.stream(new ChatCommand(conversationId,"查询预算成本列表",null),user,"t1").collectList().block();
        chat.stream(new ChatCommand(conversationId,"合计多少",null),user,"t2").collectList().block();
        assertThat(queries).hasSize(2);
        assertThat(first.toString()).contains("根据本轮结果给出预算金额");
        assertThat(requests.get(0).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactlyInAnyOrder("budgetCost.query", "budgetCost.compare", "budgetCost.clarifyDate");
        assertThat(requests.get(0).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.REQUIRED);
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("trusted-query-context");
    }
    @Test void switchingFromFundingDoesNotReuseOldDomainFilters() {
        contexts.upsert("tenant","user",conversationId,ConversationContextService.ACTIVE_BUSINESS_DOMAIN,
                "{\"domain\":\"PROJECT_FUNDING\",\"toolKey\":\"projectFunding.query\"}","old");
        contexts.upsert("tenant","user",conversationId,"QUERY_PROJECT_FUNDING",
                "{\"arguments\":{\"projectId\":\"p1\",\"startDate\":\"2020-01-01\"}}","old");
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("预算查询完成",1,1));
            assertThat(request.redactedConversationMessages().toString()).doesNotContain("trusted-query-context","2020-01-01");
            return Flux.just(new ModelEvent.ToolRequested("b","budgetCost.query","{}"));
        });
        var events=chat.stream(new ChatCommand(conversationId,"改查预算成本合计",null),user,"t").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type()));assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isNull();assertThat(queries.getFirst().startDate()).isNull();
    }
    @Test void ordinalFollowUpCarriesOnlyCurrentScopedProjectReferences() {
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("预算成本查询完成",1,1));
            if(queries.isEmpty())return Flux.just(new ModelEvent.ToolRequested("f","budgetCost.query","{\"mode\":\"detail\"}"));
            assertThat(request.redactedConversationMessages().toString()).contains("trusted-budget-cost-references","p1");
            assertThat(request.redactedConversationMessages().toString()).doesNotContain("不可见项目");
            return Flux.just(new ModelEvent.ToolRequested("f","budgetCost.query","{\"projectId\":\"p1\"}"));
        });
        chat.stream(new ChatCommand(conversationId,"查询预算成本列表",null),user,"t1").collectList().block();
        var second=chat.stream(new ChatCommand(conversationId,"看第一个项目",null),user,"t2").collectList().block();
        assertThat(second).noneMatch(e->"error".equals(e.type()));
        assertThat(queries).hasSize(2);
        assertThat(queries.get(1).projectId()).isEqualTo("p1");
    }
    private boolean matchesProjectFilter(Map<String,Object> row,com.smart.agent.tool.project.ProjectQueryFilter filter) {
        return filter.conditions().stream().allMatch(c->Objects.equals(row.get(c.field()),c.value()));
    }
    @Test void ambiguousDateAsksBeforeFetchingBusinessData() {
        ChatOrchestrator chat=orchestrator(request->Flux.just(new ModelEvent.ToolRequested("date","budgetCost.clarifyDate","{}")));
        var events=chat.stream(new ChatCommand(conversationId,"查询2026年预算成本",null),user,"t").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events.toString()).contains("clarification");
    }
    @Test void duplicateProjectNameAsksAndSelectionKeepsItemsMode() {
        projectRows=List.of(Map.of("projectId","p1","projectName","同名","projectCode","001"),
                Map.of("projectId","p2","projectName","同名","projectCode","002"));
        var scoped=new AgentUserContext("tenant","user","identity",Set.of("menu:budget:cost"),Set.of("p1","p2"));
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("预算明细查询完成",1,1));
            if(request.redactedConversationMessages().toString().contains("用户确认的项目ID=p2"))
                return Flux.just(new ModelEvent.ToolRequested("b","budgetCost.query","{\"projectId\":\"p2\",\"mode\":\"items\"}"));
            return Flux.just(new ModelEvent.ToolRequested("b","budgetCost.query","{\"projectName\":\"同名\",\"mode\":\"items\"}"));
        });
        var first=chat.stream(new ChatCommand(conversationId,"查询同名项目的预算明细",null),scoped,"t1").collectList().block();
        assertThat(first.toString()).contains("clarification");assertThat(queries).isEmpty();
        var confirmation=pending.findActive("tenant","user",conversationId).orElseThrow();
        var second=chat.stream(new ChatCommand(conversationId,"第二个",List.of(),null,
                new ChatCommand.ClarificationSelection(confirmation.id(),"project:p2")),scoped,"t2").collectList().block();
        assertThat(second).noneMatch(e->"error".equals(e.type()));assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p2");assertThat(queries.getFirst().mode()).isEqualTo("items");
    }
    @Test void dateSelectionResumesQueryWithConfirmedDimension() {
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("查询完成",1,1));
            if(pending.findActive("tenant","user",conversationId).isPresent())throw new AssertionError("pending must be resolved first");
            if(request.redactedConversationMessages().toString().contains("用户确认的日期字段=projectCreateDate"))
                return Flux.just(new ModelEvent.ToolRequested("f","budgetCost.query",
                        "{\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
            return Flux.just(new ModelEvent.ToolRequested("date","budgetCost.clarifyDate","{}"));
        });
        chat.stream(new ChatCommand(conversationId,"查2026年1月预算成本",null),user,"t1").collectList().block();
        var confirmation=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"按项目创建日期",List.of(),null,
                new ChatCommand.ClarificationSelection(confirmation.id(),"date:projectCreateDate")),user,"t2").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type()));
        assertThat(queries).hasSize(1);assertThat(queries.getFirst().dateField()).isEqualTo("projectCreateDate");
    }
    @Test void missingOneSourcePermissionRefusesBeforeAnyModelOrQuery() {
        ChatOrchestrator chat=orchestrator(request->{throw new AssertionError("must reject before model");});
        Set<String> permissions=new HashSet<>(Set.of("menu:budget:cost"));
        permissions.remove("menu:budget:cost");
        var limited=new AgentUserContext("tenant","user","identity",permissions,Set.of("p1"));
        var events=chat.stream(new ChatCommand(conversationId,"查询预算成本多少",null),limited,"t").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events).anyMatch(e->"AGENT_TOOL_FORBIDDEN".equals(e.code()));
    }
    @Test void selectingAllDatesOverridesPreviouslySavedDateRange() {
        contexts.upsert("tenant","user",conversationId,ConversationContextService.ACTIVE_BUSINESS_DOMAIN,
                "{\"domain\":\"BUDGET_COST\",\"toolKey\":\"budgetCost.query\"}","old");
        contexts.upsert("tenant","user",conversationId,"QUERY_BUDGET_COST",
                "{\"arguments\":{\"dateField\":\"projectCreateDate\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-31\"}}","old");
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("累计金额查询完成",1,1));
            String messages=request.redactedConversationMessages().toString();
            if(messages.contains("用户确认不限定日期")) {
                assertThat(messages).contains("删除dateField、startDate、endDate");
                return Flux.just(new ModelEvent.ToolRequested("f","budgetCost.query","{\"dateField\":\"projectCreateDate\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-31\"}"));
            }
            return Flux.just(new ModelEvent.ToolRequested("date","budgetCost.clarifyDate","{}"));
        });
        chat.stream(new ChatCommand(conversationId,"查2026年预算成本",null),user,"t1").collectList().block();
        var confirmation=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"不限定日期",List.of(),null,
                new ChatCommand.ClarificationSelection(confirmation.id(),"date:allProjectDates")),user,"t2").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type()));
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().dateField()).isNull();assertThat(queries.getFirst().startDate()).isNull();assertThat(queries.getFirst().endDate()).isNull();
    }
}
