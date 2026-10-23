package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.disbursement.*;
import com.smart.agent.tool.project.ProjectQueryResult;
import com.smart.agent.clarification.PendingClarificationService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class OtherPaymentRequestChatTest {
    @Test void paymentDateSelectionResumesComparisonInOtherPaymentDomain() {
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request)) return Flux.just(new ModelEvent.Completed("比较完成",1,1));
            if(request.redactedConversationMessages().toString().contains("用户确认的日期字段=paymentDate"))
                return Flux.just(new ModelEvent.ToolRequested("cmp","otherPaymentRequest.compare",
                        "{\"comparisonType\":\"YOY\",\"periodUnit\":\"MONTH\",\"current\":{\"dateField\":\"paymentDate\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}}"));
            return Flux.just(new ModelEvent.ToolRequested("date","otherPaymentRequest.clarifyDate","{}"));
        });
        chat.stream(new ChatCommand(conversationId,"比较2026年9月其他付款申请同比",null),user,"a").collectList().block();
        var question=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"实际付款时间",List.of(),null,
                new ChatCommand.ClarificationSelection(question.id(),"date:paymentDate")),user,"b").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type())); assertThat(comparisons).hasSize(1);
        assertThat(comparisons.getFirst().current().dateField()).isEqualTo("paymentDate");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void comparisonProjectSelectionPreservesBothGroupsEvenWhenModelOmitsThem(boolean bothAmbiguous) {
        projectRows=List.of(Map.of("projectId","p1","projectName","示例","projectCode","001"),
                Map.of("projectId","p2","projectName","示例","projectCode","002"));
        var expanded=new AgentUserContext("tenant","user","identity",Set.of("menu:finance:other-payment-request"),Set.of("p1","p2"));
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("比较完成",1,1));
            boolean confirmed=request.redactedConversationMessages().toString().contains("用户已确认比较条件=");
            String arguments="{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectId\":\"p1\",\"dateField\":\"createDate\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"},\"baseline\":{\"projectName\":\"示例\",\"dateField\":\"createDate\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}}";
            if(bothAmbiguous) arguments=arguments.replace("\"projectId\":\"p1\"","\"projectName\":\"示例\"");
            return Flux.just(new ModelEvent.ToolRequested("cmp","otherPaymentRequest.compare",confirmed ? "{}" : arguments));
        });
        var first=chat.stream(new ChatCommand(conversationId,"比较两个项目的其他付款申请",null),expanded,"a").collectList().block();
        assertThat(first.toString()).contains("COMPARISON_PROJECT_REFERENCE");assertThat(comparisons).isEmpty();
        if(bothAmbiguous) {
            var currentQuestion=pending.findActive("tenant","user",conversationId).orElseThrow();
            var next=chat.stream(new ChatCommand(conversationId,"第一个",List.of(),null,
                    new ChatCommand.ClarificationSelection(currentQuestion.id(),"current:project:p1")),expanded,"a2").collectList().block();
            assertThat(next.toString()).contains("COMPARISON_PROJECT_REFERENCE");assertThat(comparisons).isEmpty();
        }
        var question=pending.findActive("tenant","user",conversationId).orElseThrow();
        var second=chat.stream(new ChatCommand(conversationId,"第二个",List.of(),null,
                new ChatCommand.ClarificationSelection(question.id(),"baseline:project:p2")),expanded,"b").collectList().block();
        assertThat(second).noneMatch(e->"error".equals(e.type()));assertThat(comparisons).hasSize(1);
        assertThat(comparisons.getFirst().current().projectId()).isEqualTo("p1");
        assertThat(comparisons.getFirst().baseline().projectId()).isEqualTo("p2");
        assertThat(comparisons.getFirst().current().startDate()).isEqualTo("2026-09-01");
        assertThat(comparisons.getFirst().baseline().startDate()).isEqualTo("2026-08-01");
    }
    @Test void comparisonFollowupRefreshesAndSendsBackendResultToModel() {
        ChatOrchestrator chat=orchestrator(request->hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("其他付款申请比较完成",1,1))
                : Flux.just(new ModelEvent.ToolRequested("cmp","otherPaymentRequest.compare",
                        "{\"comparisonType\":\"YOY\",\"periodUnit\":\"MONTH\",\"current\":{\"dateField\":\"createDate\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\"}}")));
        var first=chat.stream(new ChatCommand(conversationId,"按申请创建日期比较2026年9月其他付款申请同比",null),user,"a").collectList().block();
        var second=chat.stream(new ChatCommand(conversationId,"再查一次同比",null),user,"b").collectList().block();
        assertThat(first.toString()).contains("其他付款申请比较完成");assertThat(second.toString()).contains("其他付款申请比较完成");
        assertThat(comparisons).hasSize(2);
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("trusted-query-context","2026-09-01");
        assertThat(requests.get(1).redactedConversationMessages().toString()).contains("difference");
    }
    @Test void missingComparisonPeriodPausesThenResumesWithinPaymentDomain() {
        ChatOrchestrator chat=orchestrator(request->{
            if(hasCurrentResult(request)) return Flux.just(new ModelEvent.Completed("比较完成",1,1));
            if(request.redactedConversationMessages().toString().contains("用户确认比较期间="))
                return Flux.just(new ModelEvent.ToolRequested("cmp","otherPaymentRequest.compare",
                        "{\"comparisonType\":\"YOY\",\"periodUnit\":\"MONTH\",\"current\":{\"dateField\":\"createDate\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-12\"}}"));
            return Flux.just(new ModelEvent.ToolRequested("date","otherPaymentRequest.clarifyDate","{\"ambiguity\":\"PERIOD\"}"));
        });
        chat.stream(new ChatCommand(conversationId,"按申请创建日期查其他付款申请同比",null),user,"a").collectList().block();
        assertThat(comparisons).isEmpty();
        var question=pending.findActive("tenant","user",conversationId).orElseThrow();
        var events=chat.stream(new ChatCommand(conversationId,"本月",List.of(),null,
                new ChatCommand.ClarificationSelection(question.id(),"period:MONTH")),user,"b").collectList().block();
        assertThat(events).noneMatch(e->"error".equals(e.type())); assertThat(comparisons).hasSize(1);
    }
    @Test void incomeTotalAndNextPageFollowUpsRefreshWithinTheActiveDomain() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("实时其他付款申请结果", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                queries.size() == 1 ? "{\"mode\":\"summary\"}" : "{\"mode\":\"detail\",\"page\":" + (queries.size() == 2 ? 2 : 1) + "}")));
        chat.stream(new ChatCommand(conversationId, "查询其他付款申请列表", null), user, "trace1").collectList().block();
        chat.stream(new ChatCommand(conversationId, "合计多少", null), user, "trace2").collectList().block();
        chat.stream(new ChatCommand(conversationId, "下一页", null), user, "trace3").collectList().block();
        assertThat(queries).hasSize(3);
        assertThat(queries.get(1).mode()).isEqualTo("summary");
        assertThat(queries.get(2).page()).isEqualTo(2);
        assertThat(requests.get(4).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactlyInAnyOrder("otherPaymentRequest.query", "otherPaymentRequest.clarifyDate", "otherPaymentRequest.compare");
        assertThat(requests.get(4).redactedConversationMessages().toString()).contains("trusted-query-context");
    }

    @Test void incomeWorkflowRequestsForAllTypesStillUseApproval() {
        for (String question : List.of("查询其他付款申请待办", "查询其他付款申请已办", "查询其他付款申请我发起的")) {
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
    private final AgentUserContext user = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project", "menu:finance:other-payment-request"), Set.of("p1"));
    private final List<ModelRequest> requests = new CopyOnWriteArrayList<>();
    private final List<OtherPaymentRequestQueryInput> queries = new CopyOnWriteArrayList<>();
    private final List<OtherPaymentRequestCompareInput> comparisons = new CopyOnWriteArrayList<>();
    private final String conversationId = conversationService.create("tenant", "user", "其他付款申请查询").id();
    private ToolExecutor executor;
    private List<Map<String, Object>> projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例",
            "projectCode", "20260708001"));

    @AfterEach void close() { if (executor != null) executor.close(); }

    private ChatOrchestrator orchestrator(ModelGateway model) {
        return orchestrator(model, new ChatControllerIT.TestBeans.TestApprovalBusinessClient());
    }

    private ChatOrchestrator orchestrator(ModelGateway model,
            com.smart.agent.tool.approval.ApprovalBusinessClient approvalClient) {
        OtherPaymentRequestBusinessClient client = new OtherPaymentRequestBusinessClient() {
            public ProjectQueryResult compare(ToolContext context, OtherPaymentRequestCompareInput input) {
                comparisons.add(input);
                return new ProjectQueryResult("aggregate",1,3,3L,false,List.of(),List.of(Map.of("difference",comparisons.size())),List.of());
            }
            public ProjectQueryResult query(ToolContext context, OtherPaymentRequestQueryInput input) {
            queries.add(input);
            if ("items".equals(input.mode())) return new ProjectQueryResult("detail",input.page(),input.pageSize(),2L,input.page()==1,
                    List.of(),List.of(Map.of("requestId","r1","itemId",input.page()==1?"d1":"d2","projectId","p1",
                            "registrationCode","FK-001","paymentAmount",new java.math.BigDecimal("12.34"))),List.of());
            if ("detail".equals(input.mode()) && input.requestId()!=null) return new ProjectQueryResult("detail",input.page(),input.pageSize(),1L,false,
                    List.of(),List.of(Map.of("requestId",input.requestId(),"projectId","p1","registrationCode","FK-001","remarks","主单备注")),List.of());
            if ("detail".equals(input.mode())) return new ProjectQueryResult("detail", input.page(), input.pageSize(),
                    2L, false, List.of(), List.of(Map.of("requestId", "r1", "projectId", "p1", "registrationCode", "FK-001"),
                    Map.of("requestId", "r2", "projectId", "p1", "registrationCode", "FK-002")), List.of());
            return new ProjectQueryResult("aggregate", 1, 20, 1L, false, List.of(),
                    List.of(Map.of("requestCount", queries.size(), "itemCount", queries.size(),
                            "paymentAmount", new java.math.BigDecimal("123.45"))), List.of());
        };
        };
        var projects = new com.smart.agent.tool.project.LocalProjectBusinessClient() {
            @Override public ProjectQueryResult query(ToolContext context, com.smart.agent.tool.project.ProjectQueryInput input) {
                var rows = projectRows.stream().filter(row -> context.canAccessProject(row.get("projectId").toString()))
                        .filter(row -> input.filter() == null || matchesProjectFilter(row, input.filter())).toList();
                return new ProjectQueryResult("detail", 1, 6, (long) rows.size(), false, List.of(), rows, List.of());
            }
        };
        ToolRegistry tools = new ToolRegistry(List.of(new OtherPaymentRequestQueryTool(client,
                new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new OtherPaymentRequestCompareTool(client,new com.smart.agent.tool.project.ProjectReferenceResolver(projects)),
                new com.smart.agent.tool.approval.ApprovalQueryTool(approvalClient),
                new com.smart.agent.tool.project.ProjectQueryTool(projects),
                new OtherPaymentRequestClarifyDateTool(), new com.smart.agent.tool.system.CurrentTimeTool(
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-12T02:00:00Z"), java.time.ZoneOffset.UTC),
                java.time.ZoneId.of("Asia/Shanghai"))));
        executor = new ToolExecutor(tools, runs);
        QueryIntentResolver routing = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
                new OtherPaymentRequestDomainContributor(), new ProjectDomainContributor(),
                new ApprovalDomainContributor(), new BoardDomainContributor())));
        return new ChatOrchestrator(conversationService, runs, request -> {
            requests.add(request);
            return model.stream(request);
        }, tools, executor, mapper, null, Duration.ofSeconds(10), null, null, contexts, routing, clarifications);
    }

    private boolean hasCurrentResult(ModelRequest request) {
        return request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance);
    }

    @Test void listFollowUpProvidesScopedRequestReferencesAndRefreshesSelectedItems() {
        ChatOrchestrator chat = orchestrator(request -> {
            if (hasCurrentResult(request)) return Flux.just(new ModelEvent.Completed("本轮其他付款申请查询完成", 1, 1));
            if (queries.isEmpty()) return Flux.just(new ModelEvent.ToolRequested("q", "otherPaymentRequest.query", "{}"));
            assertThat(request.redactedConversationMessages().toString())
                    .contains("trusted-other-payment-request-references", "r1", "FK-001");
            return Flux.just(new ModelEvent.ToolRequested("q", "otherPaymentRequest.query", "{\"mode\":\"items\",\"requestId\":\"r1\"}"));
        });
        chat.stream(new ChatCommand(conversationId, "查询其他付款申请列表", null), user, "t1").collectList().block();
        var events = chat.stream(new ChatCommand(conversationId, "看第一单的付款明细", null), user, "t2").collectList().block();
        assertThat(events).noneMatch(event -> "error".equals(event.type()));
        assertThat(queries).hasSize(2); assertThat(queries.get(1).requestId()).isEqualTo("r1");
        assertThat(queries.get(1).mode()).isEqualTo("items");
    }

    private boolean matchesProjectFilter(Map<String, Object> row, com.smart.agent.tool.project.ProjectQueryFilter filter) {
        var checks = new ArrayList<Boolean>();
        filter.conditions().forEach(condition -> checks.add(Objects.equals(row.get(condition.field()), condition.value())));
        filter.groups().forEach(group -> checks.add(matchesProjectFilter(row, group)));
        return "OR".equals(filter.logic()) ? checks.stream().anyMatch(Boolean::booleanValue)
                : checks.stream().allMatch(Boolean::booleanValue);
    }

    @Test void documentItemsPagingAndReturnPreserveParentAndPaymentPeriodWithLiveQueries() {
        ChatOrchestrator chat=orchestrator(request -> {
            if(hasCurrentResult(request))return Flux.just(new ModelEvent.Completed("本轮其他付款申请查询完成",1,1));
            int turn=queries.size();
            if(turn==1)assertThat(request.redactedConversationMessages().toString()).contains("trusted-other-payment-request-references","r1");
            if(turn==3 || turn==4)assertThat(request.redactedConversationMessages().toString())
                    .contains("trusted-other-payment-request-items","requestId=r1","paymentDate","2026-01-31")
                    .doesNotContain("trusted-other-payment-request-references:");
            String args=switch(turn) {
                case 0 -> "{\"mode\":\"detail\",\"page\":2";
                case 1 -> "{\"mode\":\"detail\",\"requestId\":\"r1\",\"page\":2";
                case 2 -> "{\"mode\":\"items\",\"requestId\":\"r1\",\"pageSize\":1";
                case 3 -> "{\"mode\":\"items\",\"requestId\":\"r1\",\"page\":2,\"pageSize\":1";
                default -> "{\"mode\":\"detail\",\"requestId\":\"r1\",\"page\":2";
            };
            args+=",\"dateField\":\"paymentDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}";
            return Flux.just(new ModelEvent.ToolRequested("q","otherPaymentRequest.query",args));
        });
        for(String question:List.of("按实际付款时间查询2026年1月其他付款申请第2页","查看第一笔详情","看这笔付款明细，每页1条","下一页","返回这笔申请单详情")) {
            var events=chat.stream(new ChatCommand(conversationId,question,null),user,"trace").collectList().block();
            assertThat(events).noneMatch(e -> "error".equals(e.type()));
        }
        assertThat(queries).hasSize(5);
        assertThat(queries.subList(1,5)).extracting(OtherPaymentRequestQueryInput::requestId).containsOnly("r1");
        assertThat(queries).extracting(OtherPaymentRequestQueryInput::dateField).containsOnly("paymentDate");
        assertThat(queries).extracting(OtherPaymentRequestQueryInput::endDate).containsOnly("2026-01-31");
        assertThat(queries.get(1).page()).isEqualTo(1);assertThat(queries.get(3).page()).isEqualTo(2);
        assertThat(queries.get(4).page()).isEqualTo(1);assertThat(queries.get(4).mode()).isEqualTo("detail");
    }

    @Test void toolResultReturnsToModelAndEveryFollowUpRefreshes() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("模型根据本轮其他付款申请结果回答", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("query", "otherPaymentRequest.query", "{\"mode\":\"summary\",\"projectName\":\"示例\"}")));
        var first = chat.stream(new ChatCommand(conversationId, "查询项目的其他付款申请金额", null), user, "trace1").collectList().block();
        var second = chat.stream(new ChatCommand(conversationId, "数量有多少", null), user, "trace2").collectList().block();
        assertThat(queries).hasSize(2);
        assertThat(first.toString()).contains("模型根据本轮其他付款申请结果回答");
        assertThat(second.toString()).contains("模型根据本轮其他付款申请结果回答");
        assertThat(requests.get(0).allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactlyInAnyOrder("otherPaymentRequest.query", "otherPaymentRequest.clarifyDate", "otherPaymentRequest.compare");
        assertThat(requests.get(0).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.REQUIRED);
        assertThat(requests.get(1).toolUseMode()).isEqualTo(ModelRequest.ToolUseMode.AUTO);
        assertThat(contexts.find("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN)
                .orElseThrow().payloadJson()).contains("OTHER_PAYMENT_REQUEST");
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("projectId", "p1");
    }

    @Test void historicalAnswerCannotEndTurnWithoutCurrentQuery() {
        ChatOrchestrator chat = orchestrator(request -> requests.size() == 1
                ? Flux.just(new ModelEvent.Completed("历史金额9999", 1, 1))
                : hasCurrentResult(request) ? Flux.just(new ModelEvent.Completed("实时查询后的金额", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("query", "otherPaymentRequest.query", "{\"mode\":\"summary\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "其他付款申请总金额", null), user, "trace").collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(events.stream().filter(event -> "message_delta".equals(event.type()))
                .map(ChatEvent::text).toList()).contains("实时查询后的金额").doesNotContain("历史金额9999");
    }

    @Test void projectCodeInLegacyIdFieldIsResolvedInsteadOfReportedAsForbidden() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("精确项目的其他付款申请", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                        "{\"projectId\":\"20260708001\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "看一下其他付款申请", null), user, "trace")
                .collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p1");
        assertThat(events).noneMatch(event -> "error".equals(event.type()));
    }

    @Test void projectNameDoesNotSilentlyBecomeAContainsFilter() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("精确项目的其他付款申请", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                        "{\"projectName\":\"示例\"}")));
        chat.stream(new ChatCommand(conversationId, "看一下示例项目的其他付款申请", null), user, "trace")
                .collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isEqualTo("p1");
        assertThat(queries.getFirst().projectName()).isNull();
    }

    @Test void duplicateProjectNamesRequireSelectionAndPreserveQueryFilters() {
        projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                Map.of("projectId", "p2", "projectName", "示例", "projectCode", "002"));
        var both = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project", "menu:finance:other-payment-request"), Set.of("p1", "p2"));
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("选定项目查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                        "{\"projectName\":\"示例\",\"mode\":\"summary\",\"dateField\":\"createDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}")));
        var first = chat.stream(new ChatCommand(conversationId, "示例项目1月创建的其他付款申请金额", null), both, "trace1")
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
        contexts.upsert("tenant", "user", conversationId, "QUERY_OTHER_PAYMENT_REQUEST",
                "{\"arguments\":{\"projectName\":\"旧项目\"}}", "old");
        var second = chat.stream(new ChatCommand(conversationId, "看一下其他付款申请", null), user, "trace2")
                .collectList().block();
        assertThat(requests.get(2).redactedConversationMessages().toString()).contains("projectId", "p1", "20260708001")
                .doesNotContain("旧项目");
    }

    @Test void returningFromApprovalDoesNotInheritOldIncomeFilters() {
        contexts.upsert("tenant", "user", conversationId, ConversationContextService.ACTIVE_BUSINESS_DOMAIN,
                "{\"domain\":\"APPROVAL\",\"toolKey\":\"approval.query\"}", "old");
        contexts.upsert("tenant", "user", conversationId, "QUERY_OTHER_PAYMENT_REQUEST",
                "{\"arguments\":{\"projectId\":\"old-project\",\"registrationCode\":\"old-income\"}}", "old");
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query", "{}")));
        chat.stream(new ChatCommand(conversationId, "查询其他付款申请", null), user, "trace").collectList().block();
        assertThat(requests.getFirst().redactedConversationMessages().toString())
                .doesNotContain("old-project", "old-income");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isNull();
    }

    @Test void explicitContainsKeepsDeliberateMultiProjectFilter() {
        ChatOrchestrator chat = orchestrator(request -> hasCurrentResult(request)
                ? Flux.just(new ModelEvent.Completed("查询完成", 1, 1))
                : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                        "{\"projectName\":\"示例\",\"projectNameMatch\":\"contains\"}")));
        chat.stream(new ChatCommand(conversationId, "项目名称包含示例的其他付款申请", null), user, "trace").collectList().block();
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().projectId()).isNull();
        assertThat(queries.getFirst().projectName()).isEqualTo("示例");
    }

    @Test void changedPermissionsRejectPreviouslyOfferedProject() {
        projectRows = List.of(Map.of("projectId", "p1", "projectName", "示例", "projectCode", "001"),
                Map.of("projectId", "p2", "projectName", "示例", "projectCode", "002"));
        var both = new AgentUserContext("tenant", "user", "identity", Set.of("menu:project", "menu:finance:other-payment-request"), Set.of("p1", "p2"));
        ChatOrchestrator chat = orchestrator(request -> Flux.just(new ModelEvent.ToolRequested("income",
                "otherPaymentRequest.query", "{\"projectName\":\"示例\"}")));
        chat.stream(new ChatCommand(conversationId, "查询示例项目其他付款申请", null), both, "trace1").collectList().block();
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
        var events = chat.stream(new ChatCommand(conversationId, "经营看板的其他付款申请总金额", null), boardOnly, "trace")
                .collectList().block();
        assertThat(events).anyMatch(event -> "AGENT_TOOL_FORBIDDEN".equals(event.code()));
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("当前账号还没有其他付款申请的查看权限，暂时无法为你查询。可以联系管理员确认并开通相关权限后再试。");
        assertThat(requests).isEmpty();
        assertThat(queries).isEmpty();
    }

    @Test void incomeScopeDenialNamesIncomesRatherThanProjects() {
        ChatOrchestrator chat = orchestrator(request -> Flux.just(new ModelEvent.ToolRequested(
                "income", "otherPaymentRequest.query", "{\"projectId\":\"p2\"}")));
        var events = chat.stream(new ChatCommand(conversationId, "看一下其他付款申请", null), user, "trace")
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
        var events = chat.stream(new ChatCommand(conversationId, "看一下其他付款申请",
                new ChatCommand.PageContext("project", "p2", null, null)), user, "trace").collectList().block();
        assertThat(events.stream().filter(event -> "error".equals(event.type())).map(ChatEvent::text))
                .containsExactly("当前账号暂时无法查看所查询的其他付款申请信息。可以联系管理员确认相关数据的查看范围后再试。");
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
                : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query", "{\"mode\":\"summary\"}")));
        chat.stream(new ChatCommand(conversationId, "其他付款申请总金额", null), user, "trace1").collectList().block();
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
                    entry instanceof ModelRequest.ToolResultMessage result && result.toolKey().equals("otherPaymentRequest.query"));
            return hasIncome ? Flux.just(new ModelEvent.Completed("本月其他付款申请金额", 1, 1)) : !hasTime
                    ? Flux.just(new ModelEvent.ToolRequested("time", "system.current_time", "{}"))
                    : Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                    "{\"mode\":\"summary\",\"dateField\":\"createDate\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-31\"}"));
        });
        var events = chat.stream(new ChatCommand(conversationId, "本月创建的其他付款申请金额", null), user, "trace").collectList().block();
        assertThat(events.toString()).contains("本月其他付款申请金额");
        assertThat(requests.getFirst().allowedToolSpecifications()).extracting(ModelRequest.AllowedToolSpecification::key)
                .containsExactly("system.current_time");
        assertThat(queries).hasSize(1);
    }

    @Test void selectedActualPaymentDateResumesWithTheConfirmedChildDateField() {
        ChatOrchestrator chat = orchestrator(request -> {
            if (hasCurrentResult(request)) return Flux.just(new ModelEvent.Completed("按创建日期查询完成", 1, 1));
            if (request.redactedConversationMessages().toString().contains("用户确认的日期字段=paymentDate"))
                return Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                        "{\"mode\":\"summary\",\"dateField\":\"paymentDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
            return Flux.just(new ModelEvent.ToolRequested("clarify", "otherPaymentRequest.clarifyDate", "{}"));
        });
        var events = chat.stream(new ChatCommand(conversationId, "2026年1月其他付款申请总金额", null), user, "trace1").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events.toString()).contains("date:paymentDate", "DATE_FIELD");
        var question = pending.findActive("tenant", "user", conversationId).orElseThrow();
        var answer = chat.stream(new ChatCommand(conversationId, "实际付款时间", List.of(), null,
                new ChatCommand.ClarificationSelection(question.id(), "date:paymentDate")), user, "trace2").collectList().block();
        assertThat(answer.toString()).contains("按创建日期查询完成");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().dateField()).isEqualTo("paymentDate");
    }

    @Test void ambiguousDatesPauseAndSelectedDateResumesOriginalQuery() {
        ChatOrchestrator chat = orchestrator(request -> {
            if (hasCurrentResult(request)) return Flux.just(new ModelEvent.Completed("按创建日期查询完成", 1, 1));
            if (request.redactedConversationMessages().toString().contains("用户确认的日期字段=createDate"))
                return Flux.just(new ModelEvent.ToolRequested("income", "otherPaymentRequest.query",
                        "{\"mode\":\"summary\",\"dateField\":\"createDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
            return Flux.just(new ModelEvent.ToolRequested("clarify", "otherPaymentRequest.clarifyDate", "{}"));
        });
        var events = chat.stream(new ChatCommand(conversationId, "2026年1月其他付款申请总金额", null), user, "trace1").collectList().block();
        assertThat(queries).isEmpty();
        assertThat(events.toString()).contains("date:createDate", "DATE_FIELD");
        var question = pending.findActive("tenant", "user", conversationId).orElseThrow();
        var answer = chat.stream(new ChatCommand(conversationId, "申请创建日期", List.of(), null,
                new ChatCommand.ClarificationSelection(question.id(), "date:createDate")), user, "trace2").collectList().block();
        assertThat(answer.toString()).contains("按创建日期查询完成");
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst().dateField()).isEqualTo("createDate");
    }
}
