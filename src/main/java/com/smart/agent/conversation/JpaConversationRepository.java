package com.smart.agent.conversation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(EntityManagerFactory.class)
public class JpaConversationRepository implements ConversationRepository {

    private final EntityManager entityManager;

    public JpaConversationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Conversation save(Conversation conversation) {
        if (entityManager.contains(conversation)) {
            return conversation;
        }
        if (entityManager.find(Conversation.class, conversation.id()) == null) {
            entityManager.persist(conversation);
            return conversation;
        }
        return entityManager.merge(conversation);
    }

    @Override
    public Optional<Conversation> findById(String id) {
        return Optional.ofNullable(entityManager.find(Conversation.class, id));
    }
}
