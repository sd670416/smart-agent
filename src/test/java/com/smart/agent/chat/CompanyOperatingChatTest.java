package com.smart.agent.chat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.company.*;
import com.smart.agent.tool.project.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;
class CompanyOperatingChatTest {
    @Test void allDatesSelectionRemovesModelDatesAndPreservesCompanyAndMetric() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        var queries=new ArrayList<CompanyOperatingQueryInput>();
        var tools=new ToolRegistry(List.of(new CompanyOperatingQueryTool((context,q)->{
            queries.add(q);return ProjectQueryResult.empty(1,20);
        },new ProjectReferenceResolver(new LocalProjectBusinessClient())),new CompanyOperatingClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CompanyOperatingDomainContributor(),new ProjectDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))return Flux.just(new ModelEvent.Completed("查询完成",1,1));
                if(request.redactedConversationMessages().toString().contains("用户确认不限定日期"))return Flux.just(new ModelEvent.ToolRequested("q","companyOperating.query",
                        "{\"companyId\":\"c1\",\"metric\":\"projects\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-31\"}"));
                return Flux.just(new ModelEvent.ToolRequested("d","companyOperating.clarifyDate","{}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,resolver,clarifications);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p"));String id=conversations.create("t","u","日期").id();
            chat.stream(new ChatCommand(id,"2026年按公司统计项目数量",null),user,"a").collectList().block();
            assertThat(queries).isEmpty();
            var question=pending.findActive("t","u",id).orElseThrow();
            var events=chat.stream(new ChatCommand(id,"不限定日期",List.of(),null,new ChatCommand.ClarificationSelection(question.id(),"date:allProjectDates")),user,"b").collectList().block();
            assertThat(events).noneMatch(e->"error".equals(e.type()));
            assertThat(queries).hasSize(1);
            assertThat(queries.getFirst().companyId()).isEqualTo("c1");
            assertThat(queries.getFirst().metric()).isEqualTo("projects");
            assertThat(queries.getFirst().dateField()).isNull();
            assertThat(queries.getFirst().startDate()).isNull();
            assertThat(queries.getFirst().endDate()).isNull();
        }
    }
    @Test void unclearDatePausesAndConfirmedProjectDateResumes() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger queries=new AtomicInteger();
        var tools=new ToolRegistry(List.of(new CompanyOperatingQueryTool((context,q)->{
            assertThat(q.dateField()).isEqualTo("projectCreateDate");queries.incrementAndGet();return ProjectQueryResult.empty(1,20);
        },new ProjectReferenceResolver(new LocalProjectBusinessClient())),new CompanyOperatingClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CompanyOperatingDomainContributor(),new ProjectDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))return Flux.just(new ModelEvent.Completed("查询完成",1,1));
                if(request.redactedConversationMessages().toString().contains("用户确认的日期字段=projectCreateDate"))return Flux.just(new ModelEvent.ToolRequested("q","companyOperating.query","{\"metric\":\"projects\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
                return Flux.just(new ModelEvent.ToolRequested("d","companyOperating.clarifyDate","{}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,resolver,clarifications);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p"));String id=conversations.create("t","u","日期").id();
            var first=chat.stream(new ChatCommand(id,"2026年1月按公司统计项目数量",null),user,"a").collectList().block();
            assertThat(first.toString()).contains("DATE_FIELD");assertThat(queries.get()).isZero();
            var question=pending.findActive("t","u",id).orElseThrow();
            var second=chat.stream(new ChatCommand(id,"按项目创建日期",List.of(),null,new ChatCommand.ClarificationSelection(question.id(),"date:projectCreateDate")),user,"b").collectList().block();
            assertThat(second.toString()).contains("查询完成");assertThat(queries.get()).isEqualTo(1);
        }
    }
    @Test void realtimeFollowupGetsTrustedCompanyReferenceAndReturnsNewResultToModel() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();
        var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        var calls=new ArrayList<CompanyOperatingQueryInput>();
        CompanyOperatingBusinessClient client=(context,q)->{
            calls.add(q);return new ProjectQueryResult("aggregate",1,20,1L,false,List.of(),List.of(Map.of("companyId","c","companyName","公司甲","companyCode","C1","projectCount",calls.size())),List.of());
        };
        var tools=new ToolRegistry(List.of(new CompanyOperatingQueryTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient())),new CompanyOperatingClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CompanyOperatingDomainContributor(),new ProjectDomainContributor())));
        var requests=new ArrayList<ModelRequest>();
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                requests.add(request);
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))return Flux.just(new ModelEvent.Completed("公司数据已更新",1,1));
                boolean follow=request.redactedConversationMessages().toString().contains("trusted-company-operating-references");
                return Flux.just(new ModelEvent.ToolRequested("q","companyOperating.query",follow?"{\"companyId\":\"c\",\"metric\":\"projects\"}":"{\"metric\":\"projects\"}"));
            },tools,executor,new ObjectMapper(),null,Duration.ofSeconds(10),null,null,contexts,resolver,null);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p"));
            String id=conversations.create("t","u","公司经营").id();
            var first=chat.stream(new ChatCommand(id,"按公司统计项目数量",null),user,"a").collectList().block();
            var second=chat.stream(new ChatCommand(id,"第一家有多少",null),user,"b").collectList().block();
            var third=chat.stream(new ChatCommand(id,"公司经营继续看下一页",null),user,"c").collectList().block();
            assertThat(first).noneMatch(e->"error".equals(e.type()));assertThat(second.toString()).contains("公司数据已更新");
            assertThat(third).noneMatch(e->"error".equals(e.type()));
            assertThat(calls).hasSize(3);assertThat(calls.get(1).companyId()).isEqualTo("c");assertThat(requests).hasSize(6);
        }
    }
    @Test void duplicateCompanySelectionPreservesOriginalMetricAndProjectConditions() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger moneyQueries=new AtomicInteger();
        CompanyOperatingBusinessClient client=(context,q)->{
            if("companies".equals(q.mode()))return new ProjectQueryResult("detail",1,100,2L,false,List.of(),List.of(Map.of("companyId","c1","companyName","公司","companyCode","C1"),Map.of("companyId","c2","companyName","公司","companyCode","C2")),List.of());
            assertThat(q.companyId()).isEqualTo("c2");assertThat(q.companyName()).isNull();assertThat(q.metric()).isEqualTo("contracts");moneyQueries.incrementAndGet();
            return ProjectQueryResult.empty(1,20);
        };
        var tools=new ToolRegistry(List.of(new CompanyOperatingQueryTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient())),new CompanyOperatingClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new CompanyOperatingDomainContributor(),new ProjectDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))return Flux.just(new ModelEvent.Completed("查询完成",1,1));
                // On resume the program must restore confirmed arguments, not rely on the model to repeat them.
                boolean confirmed=request.redactedConversationMessages().toString().contains("用户确认的真实公司ID");
                return Flux.just(new ModelEvent.ToolRequested("q","companyOperating.query",confirmed?"{}":"{\"companyName\":\"公司\",\"metric\":\"contracts\"}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,resolver,clarifications);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p"));String id=conversations.create("t","u","同名公司").id();
            var first=chat.stream(new ChatCommand(id,"按公司统计施工合同，公司名称是公司",null),user,"a").collectList().block();
            assertThat(first.toString()).contains("COMPANY_REFERENCE");assertThat(moneyQueries.get()).isZero();
            var question=pending.findActive("t","u",id).orElseThrow();
            var second=chat.stream(new ChatCommand(id,"C2",List.of(),null,new ChatCommand.ClarificationSelection(question.id(),"company:c2")),user,"b").collectList().block();
            assertThat(second.toString()).contains("查询完成");assertThat(moneyQueries.get()).isEqualTo(1);
        }
    }
}
