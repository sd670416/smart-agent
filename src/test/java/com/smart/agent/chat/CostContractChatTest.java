package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.cost.*;
import com.smart.agent.tool.project.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class CostContractChatTest {
    @Test void choosingNoDatesRemovesStaleDatesFromBothComparisonGroups() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger reads=new AtomicInteger();
        CostContractBusinessClient client=new CostContractBusinessClient(){
            public ProjectQueryResult query(ToolContext c,CostContractQueryInput i){throw new AssertionError("Compare only");}
            public ProjectQueryResult compare(ToolContext c,CostContractCompareInput i){
                for(var group:List.of(i.current(),i.baseline())) {
                    assertThat(group.dateField()).isNull();assertThat(group.startDate()).isNull();assertThat(group.endDate()).isNull();
                }
                assertThat(i.current().projectId()).isEqualTo("p1");assertThat(i.baseline().projectId()).isEqualTo("p2");
                reads.incrementAndGet();return new ProjectQueryResult("aggregate",1,5,0L,false,List.of(),List.of(),List.of());
            }
        };
        var resolver=new ProjectReferenceResolver(new LocalProjectBusinessClient());
        var tools=new ToolRegistry(List.of(new CostContractQueryTool(client,resolver),new CostContractCompareTool(client,resolver),new CostContractClarifyDateTool()));
        var intents=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CostContractDomainContributor(),new ProjectDomainContributor())));
        AtomicInteger turns=new AtomicInteger();
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))
                    return Flux.just(new ModelEvent.Completed("当前合同结构比较完成",1,1));
                if(turns.incrementAndGet()==1)return Flux.just(new ModelEvent.ToolRequested("c","costContract.clarifyDate","{}"));
                return Flux.just(new ModelEvent.ToolRequested("q","costContract.compare",
                        "{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectId\":\"p1\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"},\"baseline\":{\"projectId\":\"p2\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-31\"}}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,intents,clarifications);
            var user=new AgentUserContext("t","u","i",new HashSet<>(CostContractDomainContributor.permissions("all")),Set.of("p1","p2"));
            String id=conversations.create("t","u","日期确认").id();
            chat.stream(new ChatCommand(id,"比较两个项目的成本合同",null),user,"a").collectList().block();
            var question=pending.findActive("t","u",id).orElseThrow();
            var events=chat.stream(new ChatCommand(id,"不限定日期",List.of(),null,
                    new ChatCommand.ClarificationSelection(question.id(),"date:allProjectDates")),user,"b").collectList().block();
            assertThat(events.toString()).contains("当前合同结构比较完成");assertThat(reads.get()).isEqualTo(1);
        }
    }
    @Test void confirmingComparisonProjectKeepsOtherGroupAndIgnoresModelReplacement() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger reads=new AtomicInteger();
        CostContractBusinessClient client=new CostContractBusinessClient(){
            public ProjectQueryResult query(ToolContext c,CostContractQueryInput i){throw new AssertionError("Compare only");}
            public ProjectQueryResult compare(ToolContext c,CostContractCompareInput i){
                assertThat(i.current().projectId()).isEqualTo("p2");assertThat(i.current().contractName()).isEqualTo("钢");
                assertThat(i.baseline().projectId()).isEqualTo("p3");assertThat(i.baseline().contractName()).isEqualTo("木");
                assertThat(i.baseline().startDate()).isEqualTo("2026-01-01");reads.incrementAndGet();
                return new ProjectQueryResult("aggregate",1,5,0L,false,List.of(),List.of(),List.of());
            }
        };
        ProjectBusinessClient projects=org.mockito.Mockito.mock(ProjectBusinessClient.class);
        org.mockito.Mockito.when(projects.query(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProjectQueryResult("detail",1,6,2L,false,List.of(),
                        List.of(Map.of("projectId","p1","projectName","同名","projectCode","001"),
                                Map.of("projectId","p2","projectName","同名","projectCode","002")),List.of()));
        var tools=new ToolRegistry(List.of(new CostContractQueryTool(client,new ProjectReferenceResolver(projects)),
                new CostContractCompareTool(client,new ProjectReferenceResolver(projects)),new CostContractClarifyDateTool()));
        var intents=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CostContractDomainContributor(),new ProjectDomainContributor())));
        AtomicInteger turns=new AtomicInteger();
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))
                    return Flux.just(new ModelEvent.Completed("比较完成",1,1));
                return Flux.just(new ModelEvent.ToolRequested("c","costContract.compare",turns.incrementAndGet()==1
                        ?"{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectName\":\"同名\",\"contractName\":\"钢\",\"dateField\":\"projectCreateDate\"},\"baseline\":{\"projectId\":\"p3\",\"contractName\":\"木\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}}"
                        :"{}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,intents,clarifications);
            var user=new AgentUserContext("t","u","i",new HashSet<>(CostContractDomainContributor.permissions("all")),Set.of("p1","p2","p3"));
            String id=conversations.create("t","u","比较确认").id();
            var first=chat.stream(new ChatCommand(id,"比较成本合同两组结构",null),user,"a").collectList().block();
            assertThat(first.toString()).contains("COMPARISON_PROJECT_REFERENCE");assertThat(reads.get()).isZero();
            var question=pending.findActive("t","u",id).orElseThrow();
            var second=chat.stream(new ChatCommand(id,"选择第二个项目",List.of(),null,
                    new ChatCommand.ClarificationSelection(question.id(),"current:project:p2")),user,"b").collectList().block();
            assertThat(second.toString()).contains("比较完成");assertThat(reads.get()).isEqualTo(1);
        }
    }
    @Test void comparisonContextKeepsBothRealGroupsAndRefreshesSelectedCategoryDetails() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();
        var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger comparisons=new AtomicInteger(),details=new AtomicInteger();
        CostContractBusinessClient client=new CostContractBusinessClient(){
            public ProjectQueryResult compare(ToolContext context,CostContractCompareInput input){
                assertThat(input.current().projectId()).isEqualTo("p1");assertThat(input.baseline().projectId()).isEqualTo("p2");
                return new ProjectQueryResult("aggregate",1,5,5L,false,List.of(),
                        List.of(Map.of("source","material","sourceName","材料合同","currentAmount",comparisons.incrementAndGet())),
                        List.of(Map.of("comparisonGroup","current","projectIds",List.of("p1"),"contractName","钢"),
                                Map.of("comparisonGroup","baseline","projectIds",List.of("p2"),"contractName","木",
                                        "dateField","projectCreateDate","startDate","2026-01-01","endDate","2026-01-31")));
            }
            public ProjectQueryResult query(ToolContext context,CostContractQueryInput input){
                assertThat(input.projectId()).isEqualTo("p2");assertThat(input.source()).isEqualTo("material");
                assertThat(input.mode()).isEqualTo("detail");assertThat(input.contractName()).isEqualTo("木");
                assertThat(input.endDate()).isEqualTo("2026-01-31");details.incrementAndGet();
                return new ProjectQueryResult("detail",input.page(),1,2L,input.page()==1,List.of(),
                        List.of(Map.of("source","material","contractId","c"+input.page(),"projectId","p2")),List.of());
            }
        };
        var resolver=new ProjectReferenceResolver(new LocalProjectBusinessClient());
        var tools=new ToolRegistry(List.of(new CostContractQueryTool(client,resolver),new CostContractCompareTool(client,resolver),new CostContractClarifyDateTool()));
        var intents=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CostContractDomainContributor(),new ProjectDomainContributor())));
        AtomicInteger turns=new AtomicInteger();
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))
                    return Flux.just(new ModelEvent.Completed("当前合同数据已更新",1,1));
                int turn=turns.incrementAndGet();
                if(turn==1)return Flux.just(new ModelEvent.ToolRequested("a","costContract.compare",
                        "{\"comparisonType\":\"CUSTOM\",\"current\":{\"projectId\":\"p1\",\"contractName\":\"钢\",\"dateField\":\"projectCreateDate\"},\"baseline\":{\"projectId\":\"p2\",\"contractName\":\"木\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}}"));
                String messages=request.redactedConversationMessages().toString();
                if(turn==2)assertThat(messages).contains("trusted-cost-contract-comparison","p2","2026-01-31","木");
                else assertThat(messages).contains("trusted-query-context","material","p2");
                return Flux.just(new ModelEvent.ToolRequested("d","costContract.query",
                        "{\"projectId\":\"p2\",\"source\":\"material\",\"mode\":\"detail\",\"contractName\":\"木\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\",\"page\":"+(turn-1)+",\"pageSize\":1}"));
            },tools,executor,new ObjectMapper(),null,Duration.ofSeconds(10),null,null,contexts,intents,null);
            var user=new AgentUserContext("t","u","i",new HashSet<>(CostContractDomainContributor.permissions("all")),Set.of("p1","p2"));
            String id=conversations.create("t","u","成本比较").id();
            for(String question:List.of("比较两个项目当前五类合同结构","查看第二组材料合同明细","下一页")) {
                var events=chat.stream(new ChatCommand(id,question,null),user,UUID.randomUUID().toString()).collectList().block();
                assertThat(events.toString()).contains("当前合同数据已更新");assertThat(events).noneMatch(e->"error".equals(e.type()));
            }
            assertThat(comparisons.get()).isEqualTo(1);assertThat(details.get()).isEqualTo(2);
        }
    }
    @Test void unclearDatesPauseWithoutReadingAmountsAndSelectionResumes() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();
        var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();
        var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger queries=new AtomicInteger();
        CostContractBusinessClient client=(context,input)->{
            assertThat(input.dateField()).isEqualTo("projectCreateDate");queries.incrementAndGet();
            return new ProjectQueryResult("aggregate",1,20,0L,false,List.of(),List.of(),List.of());
        };
        var tools=new ToolRegistry(List.of(new CostContractQueryTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient())),new CostContractClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CostContractDomainContributor(),new ProjectDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))return Flux.just(new ModelEvent.Completed("合同查询完成",1,1));
                if(request.redactedConversationMessages().toString().contains("用户确认的日期字段=projectCreateDate"))
                    return Flux.just(new ModelEvent.ToolRequested("q","costContract.query","{\"source\":\"labor\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
                return Flux.just(new ModelEvent.ToolRequested("c","costContract.clarifyDate","{}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,resolver,clarifications);
            var user=new AgentUserContext("t","u","i",Set.of("menu:cost-contract:labor"),Set.of("p"));
            String id=conversations.create("t","u","日期确认").id();
            var first=chat.stream(new ChatCommand(id,"2026年1月劳务合同金额",null),user,"a").collectList().block();
            assertThat(first.toString()).contains("DATE_FIELD");assertThat(queries.get()).isZero();
            var question=pending.findActive("t","u",id).orElseThrow();
            var second=chat.stream(new ChatCommand(id,"项目创建日期",List.of(),null,new ChatCommand.ClarificationSelection(question.id(),"date:projectCreateDate")),user,"b").collectList().block();
            assertThat(second.toString()).contains("合同查询完成");assertThat(queries.get()).isEqualTo(1);
        }
    }
    @Test void everyFollowUpRefreshesAndReturnsResultToModelWithoutRequiringProjectMenu() {
        var repository = new ChatControllerIT.InMemoryConversationRepository();
        var conversations = new ConversationService(repository);
        var contexts = new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var runs = new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger queries = new AtomicInteger();
        CostContractBusinessClient client = (context,input) -> new ProjectQueryResult("aggregate",1,20,1L,false,List.of(),
                List.of(Map.of("contractAmount",queries.incrementAndGet())),List.of());
        var tools = new ToolRegistry(List.of(new CostContractQueryTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient())),new CostContractClarifyDateTool()));
        var resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CostContractDomainContributor(),new ProjectDomainContributor())));
        List<ModelRequest> requests = new ArrayList<>();
        try (var executor = new ToolExecutor(tools,runs)) {
            var chat = new ChatOrchestrator(conversations,runs,request -> {
                requests.add(request);
                boolean result=request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance);
                return result ? Flux.just(new ModelEvent.Completed("劳务合同金额已更新",1,1))
                        : Flux.just(new ModelEvent.ToolRequested("q","costContract.query","{\"source\":\"labor\"}"));
            },tools,executor,new ObjectMapper(),null,Duration.ofSeconds(10),null,null,contexts,resolver,null);
            var user=new AgentUserContext("t","u","i",Set.of("menu:cost-contract:labor"),Set.of("p"));
            String id=conversations.create("t","u","成本合同").id();
            var first=chat.stream(new ChatCommand(id,"查询项目劳务合同金额",null),user,"one").collectList().block();
            var second=chat.stream(new ChatCommand(id,"合计多少",null),user,"two").collectList().block();
            assertThat(first).noneMatch(e -> "error".equals(e.type()));
            assertThat(second.toString()).contains("劳务合同金额已更新");
            assertThat(queries.get()).isEqualTo(2);
            assertThat(requests).hasSize(4);
        }
    }
}
