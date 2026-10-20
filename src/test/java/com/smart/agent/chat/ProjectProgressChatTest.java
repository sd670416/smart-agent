package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.routing.*;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import com.smart.agent.tool.progress.*;
import com.smart.agent.tool.project.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class ProjectProgressChatTest {
    @Test void duplicateProjectSelectionRestoresOriginalQueryInsteadOfModelGuess() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();
        var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();
        var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        var projects=org.mockito.Mockito.mock(ProjectBusinessClient.class);
        org.mockito.Mockito.when(projects.query(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(
            new ProjectQueryResult("detail",1,6,2L,false,List.of(),List.of(
                Map.of("projectId","p1","projectName","同名项目","projectCode","001"),
                Map.of("projectId","p2","projectName","同名项目","projectCode","002")),List.of()));
        AtomicInteger queries=new AtomicInteger();
        ProjectProgressBusinessClient client=(context,input)->{
            queries.incrementAndGet();assertThat(input.projectId()).isEqualTo("p2");assertThat(input.projectName()).isNull();
            assertThat(input.mode()).isEqualTo("tasks");assertThat(input.pageSize()).isEqualTo(10);
            assertThat(input.startDate()).isEqualTo("2026-01-01");assertThat(input.endDate()).isEqualTo("2026-01-31");
            return new ProjectQueryResult("detail",1,10,1L,false,List.of(),List.of(Map.of("projectId","p2","taskId","task2")),List.of());
        };
        var tools=new ToolRegistry(List.of(new ProjectProgressQueryTool(client,new ProjectReferenceResolver(projects)),new ProjectProgressClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectProgressDomainContributor(),new ProjectDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))
                    return Flux.just(new ModelEvent.Completed("所选项目任务已更新",1,1));
                if(request.redactedConversationMessages().toString().contains("用户确认的真实项目ID=p2"))
                    return Flux.just(new ModelEvent.ToolRequested("q","projectProgress.query","{\"projectId\":\"p1\",\"mode\":\"summary\"}"));
                return Flux.just(new ModelEvent.ToolRequested("q","projectProgress.query","{\"projectName\":\"同名项目\",\"mode\":\"tasks\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\",\"pageSize\":10}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,resolver,clarifications);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p1","p2"));
            String id=conversations.create("t","u","同名项目").id();
            var first=chat.stream(new ChatCommand(id,"查看同名项目的施工进度任务，项目创建日期为2026年1月，每页10条",null),user,"a").collectList().block();
            assertThat(first.toString()).contains("PROJECT_REFERENCE");assertThat(queries.get()).isZero();
            var question=pending.findActive("t","u",id).orElseThrow();
            var second=chat.stream(new ChatCommand(id,"选择002",List.of(),null,new ChatCommand.ClarificationSelection(question.id(),"project:p2")),user,"b").collectList().block();
            assertThat(second).noneMatch(e->"error".equals(e.type()));
            assertThat(second.toString()).contains("所选项目任务已更新");assertThat(queries.get()).isEqualTo(1);
        }
    }

    @Test void switchingToTodoDoesNotCallProgressOrInheritItsProjectFilter() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();
        var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger progressQueries=new AtomicInteger(),approvalQueries=new AtomicInteger();
        var approval=org.mockito.Mockito.mock(com.smart.agent.tool.approval.ApprovalBusinessClient.class);
        org.mockito.Mockito.when(approval.query(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenAnswer(invocation->{
            com.smart.agent.tool.approval.ApprovalQueryInput input=invocation.getArgument(1);
            assertThat(input.scope()).isEqualTo("TODO");assertThat(input.filter()).isNull();approvalQueries.incrementAndGet();
            return new com.smart.agent.tool.approval.ApprovalQueryResult(1,20,1L,List.of(Map.of("taskId","approval-task")),Map.of(),List.of());
        });
        ProjectProgressBusinessClient client=(context,input)->{progressQueries.incrementAndGet();
            return new ProjectQueryResult("detail",1,20,1L,false,List.of(),List.of(Map.of("projectId","p1")),List.of());};
        var tools=new ToolRegistry(List.of(new ProjectProgressQueryTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient())),
            new ProjectProgressClarifyDateTool(),new com.smart.agent.tool.approval.ApprovalQueryTool(approval)));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectProgressDomainContributor(),new ProjectDomainContributor(),new ApprovalDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))
                    return Flux.just(new ModelEvent.Completed("查询已更新",1,1));
                if(request.redactedConversationMessages().toString().contains("改查待办")) {
                    assertThat(request.allowedToolSpecifications()).anyMatch(t->"approval.query".equals(t.key()));
                    assertThat(request.allowedToolSpecifications()).noneMatch(t->"projectProgress.query".equals(t.key()));
                    return Flux.just(new ModelEvent.ToolRequested("a","approval.query","{\"scope\":\"TODO\"}"));
                }
                return Flux.just(new ModelEvent.ToolRequested("p","projectProgress.query","{\"projectId\":\"p1\",\"mode\":\"tasks\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
            },tools,executor,new ObjectMapper(),null,Duration.ofSeconds(10),null,null,contexts,resolver,null);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p1"));
            String id=conversations.create("t","u","切换查询").id();
            chat.stream(new ChatCommand(id,"查看施工进度",null),user,"a").collectList().block();
            var second=chat.stream(new ChatCommand(id,"改查待办",null),user,"b").collectList().block();
            assertThat(second).noneMatch(e->"error".equals(e.type()));
            assertThat(second.toString()).contains("查询已更新");
            assertThat(progressQueries.get()).isEqualTo(1);assertThat(approvalQueries.get()).isEqualTo(1);
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void unclearDatesPauseAndSelectionResumesWithoutGuessing(boolean allDates) {
        var repository=new ChatControllerIT.InMemoryConversationRepository();
        var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var pending=new ChatControllerIT.InMemoryPendingClarificationRepository();
        var mapper=new ObjectMapper();
        var clarifications=new com.smart.agent.clarification.PendingClarificationService(pending,mapper);
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger queries=new AtomicInteger();
        ProjectProgressBusinessClient client=(context,input)->{
            queries.incrementAndGet();
            assertThat(input.dateField()).isEqualTo(allDates?null:"projectCreateDate");
            assertThat(input.startDate()).isEqualTo(allDates?null:"2026-01-01");
            assertThat(input.endDate()).isEqualTo(allDates?null:"2026-01-31");
            return new ProjectQueryResult("aggregate",1,20,1L,false,List.of(),List.of(Map.of("averageProgress",40)),List.of());
        };
        var tools=new ToolRegistry(List.of(new ProjectProgressQueryTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient())),new ProjectProgressClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectProgressDomainContributor(),new ProjectDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))
                    return Flux.just(new ModelEvent.Completed("进度查询完成",1,1));
                String messages=request.redactedConversationMessages().toString();
                if(messages.contains("用户确认的日期字段=projectCreateDate"))
                    return Flux.just(new ModelEvent.ToolRequested("q","projectProgress.query","{\"mode\":\"summary\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
                if(messages.contains("用户确认不限定日期"))
                    return Flux.just(new ModelEvent.ToolRequested("q","projectProgress.query","{\"mode\":\"summary\",\"dateField\":\"projectCreateDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}"));
                return Flux.just(new ModelEvent.ToolRequested("c","projectProgress.clarifyDate","{}"));
            },tools,executor,mapper,null,Duration.ofSeconds(10),null,null,contexts,resolver,clarifications);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p1"));
            String id=conversations.create("t","u","日期确认").id();
            var first=chat.stream(new ChatCommand(id,"2026年1月施工进度",null),user,"a").collectList().block();
            assertThat(first.toString()).contains("DATE_FIELD");assertThat(queries.get()).isZero();
            var question=pending.findActive("t","u",id).orElseThrow();
            var second=chat.stream(new ChatCommand(id,allDates?"不限定日期":"项目创建日期",List.of(),null,
                new ChatCommand.ClarificationSelection(question.id(),allDates?"date:allProjectDates":"date:projectCreateDate")),user,"b").collectList().block();
            assertThat(second).noneMatch(e->"error".equals(e.type()));
            assertThat(second.toString()).contains("进度查询完成");assertThat(queries.get()).isEqualTo(1);
        }
    }
    @Test void ordinalFollowUpGetsTrustedProjectReferencesAndRefreshesResults() {
        var repository=new ChatControllerIT.InMemoryConversationRepository();
        var conversations=new ConversationService(repository);
        var contexts=new ConversationContextService(new ChatControllerIT.InMemoryConversationContextRepository());
        var runs=new AgentRunService(new ChatControllerIT.InMemoryAgentRunRepository(),repository,new ChatControllerIT.InMemoryAgentRunStepRepository());
        AtomicInteger queries=new AtomicInteger();
        ProjectProgressBusinessClient client=(context,input)->{
            queries.incrementAndGet();
            if("tasks".equals(input.mode())) {
                assertThat(input.projectId()).isEqualTo("p2");
                return new ProjectQueryResult("detail",1,20,1L,false,List.of(),List.of(Map.of("taskId","task2","projectId","p2","taskName","施工任务")),List.of());
            }
            return new ProjectQueryResult("detail",1,20,2L,false,List.of(),List.of(
                Map.of("projectId","p1","projectCode","001","projectName","甲项目"),
                Map.of("projectId","p2","projectCode","002","projectName","乙项目")),List.of());
        };
        var tools=new ToolRegistry(List.of(new ProjectProgressQueryTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient())),new ProjectProgressClarifyDateTool()));
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectProgressDomainContributor(),new ProjectDomainContributor())));
        try(var executor=new ToolExecutor(tools,runs)) {
            var chat=new ChatOrchestrator(conversations,runs,request->{
                if(request.redactedConversationMessages().stream().anyMatch(ModelRequest.ToolResultMessage.class::isInstance))
                    return Flux.just(new ModelEvent.Completed("进度已更新",1,1));
                if(request.redactedConversationMessages().toString().contains("第二个")) {
                    assertThat(request.redactedConversationMessages().toString()).contains("pagePosition","p2","002");
                    return Flux.just(new ModelEvent.ToolRequested("tasks","projectProgress.query","{\"projectId\":\"p2\",\"mode\":\"tasks\"}"));
                }
                return Flux.just(new ModelEvent.ToolRequested("list","projectProgress.query","{\"mode\":\"detail\"}"));
            },tools,executor,new ObjectMapper(),null,Duration.ofSeconds(10),null,null,contexts,resolver,null);
            var user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p1","p2"));
            String id=conversations.create("t","u","施工进度").id();
            var first=chat.stream(new ChatCommand(id,"查询施工进度",null),user,"one").collectList().block();
            var second=chat.stream(new ChatCommand(id,"第二个项目的任务明细",null),user,"two").collectList().block();
            assertThat(first).noneMatch(e->"error".equals(e.type()));
            assertThat(second).noneMatch(e->"error".equals(e.type()));
            assertThat(second.toString()).contains("进度已更新");
            assertThat(queries.get()).isEqualTo(2);
        }
    }
}
