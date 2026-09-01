package com.smart.agent.conversation;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(EntityManagerFactory.class)
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
    public Message appendMessage(String conversationId, Message.Role role, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        Message message = conversation.append(role, content);
        conversationRepository.save(conversation);
        return message;
    }
}
