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
