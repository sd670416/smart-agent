package com.smart.agent.context;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaConversationContextRepository implements ConversationContextRepository {
    private final EntityManager entityManager;

    public JpaConversationContextRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override
    public ConversationContext save(ConversationContext context) {
        if (entityManager.contains(context)) return context;
        if (entityManager.find(ConversationContext.class, context.id()) == null) {
            entityManager.persist(context);
            return context;
        }
        return entityManager.merge(context);
    }

    @Override
    public Optional<ConversationContext> find(
            String tenantId, String userId, String conversationId, String contextType) {
        return entityManager.createQuery("select c from ConversationContext c "
                        + "where c.tenantId = :tenantId and c.userId = :userId "
                        + "and c.conversationId = :conversationId and c.contextType = :contextType",
                        ConversationContext.class)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", userId)
                .setParameter("conversationId", conversationId)
                .setParameter("contextType", contextType)
                .getResultList().stream().findFirst();
    }
}
