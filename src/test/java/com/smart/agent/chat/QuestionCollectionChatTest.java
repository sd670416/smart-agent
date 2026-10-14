package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.*;
import com.smart.agent.question.QuestionRecordService;
import com.smart.agent.run.*;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionCollectionChatTest {
    private final ChatControllerIT.InMemoryConversationRepository conversations=new ChatControllerIT.InMemoryConversationRepository();
    private final ChatControllerIT.InMemoryAgentRunRepository repository=new ChatControllerIT.InMemoryAgentRunRepository();
    private final ConversationService conversationService=new ConversationService(conversations);
    private final AgentRunService runs=new AgentRunService(repository,conversations,new ChatControllerIT.InMemoryAgentRunStepRepository());
    private final AgentUserContext user=new AgentUserContext("t","u","i",Set.of(),Set.of());
    private final String conversationId=conversationService.create("t","u","questions").id();
    private List<String> execute(ModelGateway model) {
        List<String> sources=new ArrayList<>();
        QuestionRecordService collector=mock(QuestionRecordService.class);
        doAnswer(invocation->{
            AgentRun run=repository.findByIdAndTenantIdAndUserId("t","u",invocation.getArgument(1)).orElseThrow();
            assertThat(run.userMessageId()).isNotBlank();
            assertThat(run.assistantMessageId()).isNotBlank();
            sources.add(invocation.getArgument(2)); return null;
        }).when(collector).collectSafely(any(),anyString(),anyString(),nullable(String.class),anyString());
        ToolRegistry registry=new ToolRegistry(List.of());
        try(ToolExecutor executor=new ToolExecutor(registry,runs)) {
            ChatOrchestrator orchestrator=new ChatOrchestrator(conversationService,runs,model,registry,executor,new ObjectMapper(),null,Duration.ofSeconds(10));
            orchestrator.setQuestionRecords(collector);
            orchestrator.stream(new ChatCommand(conversationId,"hello",null),user,"trace").collectList().block(Duration.ofSeconds(10));
        }
        return sources;
    }
    @Test void terminalModelFailureCollectsSystemWithRealMessageBindings() {
        assertThat(execute(request->Flux.just(new ModelEvent.Failed("MODEL_FAILED","private error")))).containsExactly("SYSTEM");
    }
    @Test void normalTextMentioningUnsupportedDoesNotCollect() {
        assertThat(execute(request->Flux.just(new ModelEvent.Completed("unsupported is just a word",1,1)))).isEmpty();
    }
    @Test void structuredMarkerCollectsOnlyAfterFinalCompletion() {
        AtomicInteger calls=new AtomicInteger();
        assertThat(execute(request->calls.getAndIncrement()==0 ? Flux.just(
                new ModelEvent.ToolRequested("mark","system.unsupported_capability","{\"reason\":\"field unavailable\"}"),
                new ModelEvent.Completed("",1,1)) : Flux.just(new ModelEvent.Completed("This field is unavailable",1,1))))
                .containsExactly("MODEL");
    }
}
