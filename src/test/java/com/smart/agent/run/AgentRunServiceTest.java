package com.smart.agent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.ConversationRepository;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentRunServiceTest {

    @Test
    void rejectsInvalidRunTransition() {
        AgentRun run = AgentRun.start("tenant-1", "user-1", "conversation-1");

        assertThatThrownBy(() -> run.transition(AgentRunStatus.COMPLETED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitionsThroughPlanningAndPersistsTheNewStatus() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        Conversation conversation = conversations.save(Conversation.create("tenant-1", "user-1", "项目问答"));
        InMemoryAgentRunRepository runs = new InMemoryAgentRunRepository();
        AgentRunService service = new AgentRunService(runs, conversations);
        AgentRun run = service.start("tenant-1", "user-1", conversation.id());

        service.transition("tenant-1", "user-1", run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.ROUTING);
        AgentRun transitioned = service.transition("tenant-1", "user-1", run.id(), AgentRunStatus.ROUTING, AgentRunStatus.PLANNING);

        assertThat(transitioned.status()).isEqualTo(AgentRunStatus.PLANNING);
        assertThat(runs.findByIdAndTenantIdAndUserId("tenant-1", "user-1", run.id()).orElseThrow().status())
                .isEqualTo(AgentRunStatus.PLANNING);
    }

    @Test
    void rejectsCrossTenantAndCrossUserRunStartAndTransitionWithoutSaving() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        Conversation conversation = conversations.save(Conversation.create("tenant-1", "user-1", "项目问答"));
        InMemoryAgentRunRepository runs = new InMemoryAgentRunRepository();
        AgentRunService service = new AgentRunService(runs, conversations);

        assertThatThrownBy(() -> service.start("tenant-2", "user-1", conversation.id())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start("tenant-1", "user-2", conversation.id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(runs.saveCount()).isZero();

        AgentRun run = service.start("tenant-1", "user-1", conversation.id());
        int savesBeforeRejectedTransitions = runs.saveCount();
        assertThatThrownBy(() -> service.transition("tenant-2", "user-1", run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.ROUTING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transition("tenant-1", "user-2", run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.ROUTING))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(runs.saveCount()).isEqualTo(savesBeforeRejectedTransitions);
        assertThat(runs.findByIdAndTenantIdAndUserId("tenant-1", "user-1", run.id()).orElseThrow().status())
                .isEqualTo(AgentRunStatus.RECEIVED);
    }

    @Test
    void rejectsTransitionWhenExpectedStatusIsStale() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        Conversation conversation = conversations.save(Conversation.create("tenant-1", "user-1", "项目问答"));
        InMemoryAgentRunRepository runs = new InMemoryAgentRunRepository();
        AgentRunService service = new AgentRunService(runs, conversations);
        AgentRun run = service.start("tenant-1", "user-1", conversation.id());
        service.transition("tenant-1", "user-1", run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.ROUTING);

        assertThatThrownBy(() -> service.transition("tenant-1", "user-1", run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.PLANNING))
                .isInstanceOf(IllegalStateException.class);
        assertThat(runs.findByIdAndTenantIdAndUserId("tenant-1", "user-1", run.id()).orElseThrow().status())
                .isEqualTo(AgentRunStatus.ROUTING);
    }

    @Test
    void rejectsInvalidRunInputs() {
        assertThatThrownBy(() -> AgentRun.start(" ", "user-1", "conversation-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentRun.start("tenant-1", null, "conversation-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentRun.start("tenant-1", "user-1", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class InMemoryConversationRepository implements ConversationRepository {
        private final Map<String, Conversation> conversations = new LinkedHashMap<>();

        @Override
        public Conversation save(Conversation conversation) {
            conversations.put(conversation.id(), conversation);
            return conversation;
        }

        @Override
        public Optional<Conversation> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id) {
            return Optional.ofNullable(conversations.get(id))
                    .filter(conversation -> conversation.tenantId().equals(tenantId) && conversation.userId().equals(userId));
        }
    }

    private static final class InMemoryAgentRunRepository implements AgentRunRepository {
        private final Map<String, AgentRunSnapshot> runs = new LinkedHashMap<>();
        private int saveCount;

        @Override
        public AgentRun save(AgentRun run) {
            runs.put(run.id(), AgentRunSnapshot.from(run));
            saveCount++;
            return AgentRunSnapshot.from(run).restore();
        }

        @Override
        public Optional<AgentRun> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id) {
            return Optional.ofNullable(runs.get(id))
                    .filter(snapshot -> snapshot.tenantId().equals(tenantId) && snapshot.userId().equals(userId))
                    .map(AgentRunSnapshot::restore);
        }

        int saveCount() {
            return saveCount;
        }
    }

    private record AgentRunSnapshot(String id, String tenantId, String userId, String conversationId, AgentRunStatus status) {
        static AgentRunSnapshot from(AgentRun run) {
            return new AgentRunSnapshot(run.id(), run.tenantId(), run.userId(), run.conversationId(), run.status());
        }

        AgentRun restore() {
            AgentRun run = AgentRun.start(tenantId, userId, conversationId);
            setField(run, "id", id);
            setField(run, "status", status);
            return run;
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
