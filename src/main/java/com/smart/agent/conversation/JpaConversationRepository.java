package com.smart.agent.conversation;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    public Optional<Conversation> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id) {
        return entityManager.createQuery("select distinct c from Conversation c left join fetch c.messages "
                        + "where c.id = :id and c.tenantId = :tenantId and c.userId = :userId", Conversation.class)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public void delete(Conversation conversation) {
        // 会话下的运行记录、运行步骤和引用记录没有配置级联，必须先按租户和用户清理，
        // 否则删除 ai_conversation 时会被外键 fk_ai_run_conversation 拒绝。
        entityManager.createNativeQuery("DELETE FROM ai_citation WHERE tenant_id = :tenantId AND user_id = :userId "
                        + "AND run_id IN (SELECT id FROM ai_run WHERE conversation_id = :conversationId "
                        + "AND tenant_id = :tenantId AND user_id = :userId)")
                .setParameter("tenantId", conversation.tenantId())
                .setParameter("userId", conversation.userId())
                .setParameter("conversationId", conversation.id())
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM ai_run_step WHERE tenant_id = :tenantId AND user_id = :userId "
                        + "AND run_id IN (SELECT id FROM ai_run WHERE conversation_id = :conversationId "
                        + "AND tenant_id = :tenantId AND user_id = :userId)")
                .setParameter("tenantId", conversation.tenantId())
                .setParameter("userId", conversation.userId())
                .setParameter("conversationId", conversation.id())
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM ai_run WHERE conversation_id = :conversationId "
                        + "AND tenant_id = :tenantId AND user_id = :userId")
                .setParameter("conversationId", conversation.id())
                .setParameter("tenantId", conversation.tenantId())
                .setParameter("userId", conversation.userId())
                .executeUpdate();
        entityManager.remove(entityManager.contains(conversation) ? conversation : entityManager.merge(conversation));
    }

    @Override
    public List<Conversation> findByTenantIdAndUserId(String tenantId, String userId) {
        return entityManager.createQuery("select c from Conversation c where c.tenantId = :tenantId and c.userId = :userId order by c.updatedAt desc", Conversation.class)
                .setParameter("tenantId", tenantId).setParameter("userId", userId).getResultList();
    }
}
