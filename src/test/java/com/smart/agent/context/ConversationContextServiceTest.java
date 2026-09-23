package com.smart.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ConversationContextServiceTest {
    private final InMemoryRepository repository = new InMemoryRepository();
    private final ConversationContextService service = new ConversationContextService(repository);

    @Test
    void upsertReplacesOnlyTheSameScopedContext() {
        service.upsert("tenant-1", "user-1", "conversation-1", "APPROVAL_QUERY", "{\"page\":1}", "run-1");
        service.upsert("tenant-1", "user-1", "conversation-1", "APPROVAL_QUERY", "{\"page\":2}", "run-2");
        service.upsert("tenant-1", "user-2", "conversation-1", "APPROVAL_QUERY", "{\"page\":9}", "run-9");

        ConversationContext current = service.find(
                "tenant-1", "user-1", "conversation-1", "APPROVAL_QUERY").orElseThrow();
        assertThat(current.payloadJson()).isEqualTo("{\"page\":2}");
        assertThat(current.sourceRunId()).isEqualTo("run-2");
        assertThat(service.find("tenant-1", "user-2", "conversation-1", "APPROVAL_QUERY"))
                .get().extracting(ConversationContext::payloadJson).isEqualTo("{\"page\":9}");
    }

    @Test
    void rejectsOversizedPayload() {
        assertThatThrownBy(() -> service.upsert("tenant-1", "user-1", "conversation-1",
                "APPROVAL_QUERY", "x".repeat(ConversationContextService.MAX_PAYLOAD_BYTES + 1), "run-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too large");
    }

    private static final class InMemoryRepository implements ConversationContextRepository {
        private final Map<String, ConversationContext> values = new ConcurrentHashMap<>();
        @Override public ConversationContext save(ConversationContext context) {
            values.put(key(context.tenantId(), context.userId(), context.conversationId(), context.contextType()), context);
            return context;
        }
        @Override public Optional<ConversationContext> find(
                String tenantId, String userId, String conversationId, String contextType) {
            return Optional.ofNullable(values.get(key(tenantId, userId, conversationId, contextType)));
        }
        private static String key(String tenantId, String userId, String conversationId, String contextType) {
            return String.join("/", tenantId, userId, conversationId, contextType);
        }
    }
}
