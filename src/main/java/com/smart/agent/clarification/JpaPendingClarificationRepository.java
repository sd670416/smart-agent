package com.smart.agent.clarification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaPendingClarificationRepository implements PendingClarificationRepository {
    private final EntityManager entityManager;

    public JpaPendingClarificationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public PendingClarification save(PendingClarification value) {
        if (entityManager.contains(value)) return value;
        if (entityManager.find(PendingClarification.class, value.id()) == null) {
            entityManager.persist(value);
            return value;
        }
        return entityManager.merge(value);
    }

    @Override
    public void flush() {
        entityManager.flush();
    }

    @Override
    public Optional<PendingClarification> findByIdAndScope(
            String id, String tenantId, String userId, String conversationId) {
        return entityManager.createQuery("select c from PendingClarification c "
                        + "where c.id = :id and c.tenantId = :tenantId and c.userId = :userId "
                        + "and c.conversationId = :conversationId", PendingClarification.class)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", userId)
                .setParameter("conversationId", conversationId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList().stream().findFirst();
    }

    @Override
    public Optional<PendingClarification> findActive(String tenantId, String userId, String conversationId) {
        return entityManager.createQuery("select c from PendingClarification c "
                        + "where c.tenantId = :tenantId and c.userId = :userId "
                        + "and c.conversationId = :conversationId and c.status = :status "
                        + "order by c.createdAt desc", PendingClarification.class)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", userId)
                .setParameter("conversationId", conversationId)
                .setParameter("status", PendingClarification.Status.PENDING)
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList().stream().findFirst();
    }
}
