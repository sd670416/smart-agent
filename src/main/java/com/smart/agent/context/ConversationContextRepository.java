package com.smart.agent.context;

import java.util.Optional;

public interface ConversationContextRepository {
    ConversationContext save(ConversationContext context);
    Optional<ConversationContext> find(String tenantId, String userId, String conversationId, String contextType);
}
