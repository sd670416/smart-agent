package com.smart.agent.clarification;

import java.util.Optional;

public interface PendingClarificationRepository {
    PendingClarification save(PendingClarification value);
    default void flush() {}
    Optional<PendingClarification> findByIdAndScope(
            String id, String tenantId, String userId, String conversationId);
    Optional<PendingClarification> findActive(String tenantId, String userId, String conversationId);
}
