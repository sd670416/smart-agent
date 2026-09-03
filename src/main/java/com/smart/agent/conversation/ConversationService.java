package com.smart.agent.conversation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public Conversation create(String tenantId, String userId, String title) {
        return conversationRepository.save(Conversation.create(tenantId, userId, title));
    }

    @Transactional
    public Message appendMessage(String tenantId, String userId, String conversationId, Message.Role role, String content) {
        Conversation conversation = conversationRepository.findByIdAndTenantIdAndUserId(
                        requireText(tenantId, "tenantId"), requireText(userId, "userId"), requireText(conversationId, "conversationId"))
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        Message message = conversation.append(role, content);
        conversationRepository.save(conversation);
        return message;
    }

    @Transactional(readOnly = true)
    public Conversation find(String tenantId, String userId, String conversationId) {
        return conversationRepository.findByIdAndTenantIdAndUserId(tenantId, userId, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
