package com.smart.agent.clarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PendingClarificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-28T02:00:00Z");
    private final InMemoryRepository repository = new InMemoryRepository();
    private final PendingClarificationService service = new PendingClarificationService(
            repository, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void isolatesPendingClarificationByTenantUserAndConversation() {
        PendingClarification created = create("tenant-1", "user-1", "conversation-1");

        assertThat(service.findActive("tenant-1", "user-1", "conversation-1"))
                .get().extracting(PendingClarification::id).isEqualTo(created.id());
        assertThat(service.findActive("tenant-2", "user-1", "conversation-1")).isEmpty();
        assertThat(service.findActive("tenant-1", "user-2", "conversation-1")).isEmpty();
        assertThat(service.findActive("tenant-1", "user-1", "conversation-2")).isEmpty();
        assertThatThrownBy(() -> service.resolve(
                "tenant-1", "user-2", "conversation-1", created.id(), "project_status"))
                .isInstanceOf(PendingClarificationException.class)
                .hasMessageContaining("不存在或已失效");
    }

    @Test
    void rejectsExpiredClarification() {
        PendingClarification created = PendingClarification.create(
                "tenant-1", "user-1", "conversation-1", "PROJECT",
                "{\"question\":\"按状态统计\"}", optionsJson(), NOW.minusSeconds(1));
        repository.save(created);

        assertThat(service.findActive("tenant-1", "user-1", "conversation-1")).isEmpty();
        assertThat(created.status()).isEqualTo(PendingClarification.Status.EXPIRED);
        assertThatThrownBy(() -> service.resolve(
                "tenant-1", "user-1", "conversation-1", created.id(), "project_status"))
                .isInstanceOf(PendingClarificationException.class)
                .hasMessageContaining("不存在或已失效");
    }

    @Test
    void consumesAClarificationOnlyOnce() {
        PendingClarification created = create("tenant-1", "user-1", "conversation-1");

        PendingClarification resolved = service.resolve(
                "tenant-1", "user-1", "conversation-1", created.id(), "project_status");

        assertThat(resolved.status()).isEqualTo(PendingClarification.Status.RESOLVED);
        assertThat(resolved.selectedOptionId()).isEqualTo("project_status");
        assertThatThrownBy(() -> service.resolve(
                "tenant-1", "user-1", "conversation-1", created.id(), "approval_status"))
                .isInstanceOf(PendingClarificationException.class)
                .hasMessageContaining("不存在或已失效");
    }

    @Test
    void creatingANewClarificationCancelsThePreviousPendingOne() {
        PendingClarification previous = create("tenant-1", "user-1", "conversation-1");

        PendingClarification current = create("tenant-1", "user-1", "conversation-1");

        assertThat(previous.status()).isEqualTo(PendingClarification.Status.CANCELLED);
        assertThat(service.findActive("tenant-1", "user-1", "conversation-1"))
                .get().extracting(PendingClarification::id).isEqualTo(current.id());
    }

    @Test
    void rejectsUnknownOptionAndOversizedPayload() {
        PendingClarification created = create("tenant-1", "user-1", "conversation-1");
        assertThatThrownBy(() -> service.resolve(
                "tenant-1", "user-1", "conversation-1", created.id(), "invented"))
                .isInstanceOf(PendingClarificationException.class)
                .hasMessageContaining("选项无效");

        assertThatThrownBy(() -> service.create("tenant-1", "user-1", "conversation-1", "PROJECT",
                "x".repeat(PendingClarificationService.MAX_JSON_BYTES + 1), optionsJson(), Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too large");
    }

    private PendingClarification create(String tenantId, String userId, String conversationId) {
        return service.create(tenantId, userId, conversationId, "PROJECT",
                "{\"question\":\"按状态统计\"}", optionsJson(), Duration.ofMinutes(5));
    }

    private static String optionsJson() {
        return "[{\"id\":\"project_status\",\"label\":\"项目状态\"},"
                + "{\"id\":\"approval_status\",\"label\":\"审批状态\"}]";
    }

    private static final class InMemoryRepository implements PendingClarificationRepository {
        private final Map<String, PendingClarification> values = new LinkedHashMap<>();

        @Override public synchronized PendingClarification save(PendingClarification value) {
            values.put(value.id(), value);
            return value;
        }

        @Override public synchronized Optional<PendingClarification> findByIdAndScope(
                String id, String tenantId, String userId, String conversationId) {
            PendingClarification value = values.get(id);
            if (value == null || !value.belongsTo(tenantId, userId, conversationId)) return Optional.empty();
            return Optional.of(value);
        }

        @Override public synchronized Optional<PendingClarification> findActive(
                String tenantId, String userId, String conversationId) {
            return values.values().stream()
                    .filter(value -> value.belongsTo(tenantId, userId, conversationId))
                    .filter(value -> value.status() == PendingClarification.Status.PENDING)
                    .reduce((first, second) -> second);
        }
    }
}
