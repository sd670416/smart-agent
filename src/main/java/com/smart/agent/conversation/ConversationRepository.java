package com.smart.agent.conversation;

import java.util.Optional;

public interface ConversationRepository {
    Conversation save(Conversation conversation);

    Optional<Conversation> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id);
}
