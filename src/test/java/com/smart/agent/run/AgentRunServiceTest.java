package com.smart.agent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentRunServiceTest {

    @Test
    void rejectsInvalidRunTransition() {
        AgentRun run = AgentRun.start("tenant-1", "user-1", "conversation-1");

        assertThatThrownBy(() -> run.transition(AgentRunStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitionsThroughPlanningAndPersistsTheNewStatus() {
        InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
        AgentRunService service = new AgentRunService(repository);
        AgentRun run = service.start("tenant-1", "user-1", "conversation-1");

        service.transition(run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.ROUTING);
        AgentRun transitioned = service.transition(run.id(), AgentRunStatus.ROUTING, AgentRunStatus.PLANNING);

        assertThat(transitioned.status()).isEqualTo(AgentRunStatus.PLANNING);
        assertThat(repository.findById(run.id()).orElseThrow().status()).isEqualTo(AgentRunStatus.PLANNING);
    }

    @Test
    void rejectsTransitionWhenExpectedStatusIsStale() {
        InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
        AgentRunService service = new AgentRunService(repository);
        AgentRun run = service.start("tenant-1", "user-1", "conversation-1");
        service.transition(run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.ROUTING);

        assertThatThrownBy(() -> service.transition(run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.PLANNING))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(run.id()).orElseThrow().status()).isEqualTo(AgentRunStatus.ROUTING);
    }

    private static final class InMemoryAgentRunRepository implements AgentRunRepository {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();

        @Override
        public AgentRun save(AgentRun run) {
            runs.put(run.id(), run);
            return run;
        }

        @Override
        public Optional<AgentRun> findById(String id) {
            return Optional.ofNullable(runs.get(id));
        }
    }
}
