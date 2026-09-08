package com.smart.agent.conversation;

import java.util.Optional;
import java.util.List;

public interface ConversationRepository {
    Conversation save(Conversation conversation);
    default void delete(Conversation conversation) { throw new UnsupportedOperationException("delete is not supported"); }

    Optional<Conversation> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id);

    default List<Conversation> findByTenantIdAndUserId(String tenantId, String userId) {
        return java.util.Collections.emptyList();
    }
}
