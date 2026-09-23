package com.smart.agent.context;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConversationContextService {
    public static final String APPROVAL_QUERY = "APPROVAL_QUERY";
    static final int MAX_PAYLOAD_BYTES = 256 * 1024;

    private final ConversationContextRepository repository;

    public ConversationContextService(ConversationContextRepository repository) { this.repository = repository; }

    @Transactional
    public ConversationContext upsert(String tenantId, String userId, String conversationId,
            String contextType, String payloadJson, String sourceRunId) {
        requirePayloadSize(payloadJson);
        ConversationContext context = repository.find(tenantId, userId, conversationId, contextType)
                .orElseGet(() -> ConversationContext.create(
                        tenantId, userId, conversationId, contextType, payloadJson, sourceRunId));
        if (context.id() != null) context.update(payloadJson, sourceRunId);
        return repository.save(context);
    }

    @Transactional(readOnly = true)
    public Optional<ConversationContext> find(String tenantId, String userId, String conversationId,
            String contextType) {
        return repository.find(tenantId, userId, conversationId, contextType);
    }

    private void requirePayloadSize(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson must not be blank");
        }
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("conversation context payload is too large");
        }
    }
}
