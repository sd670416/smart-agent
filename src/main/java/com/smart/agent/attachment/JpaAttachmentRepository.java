package com.smart.agent.attachment;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaAttachmentRepository implements AttachmentRepository {

    private final EntityManager entityManager;

    public JpaAttachmentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Attachment save(Attachment attachment) {
        if (entityManager.contains(attachment)) return attachment;
        if (entityManager.find(Attachment.class, attachment.id()) == null) {
            entityManager.persist(attachment);
            return attachment;
        }
        return entityManager.merge(attachment);
    }

    @Override
    public Optional<Attachment> findByIdAndTenantIdAndUserId(UUID id, String tenantId, String userId) {
        return entityManager.createQuery("select a from Attachment a where a.id = :id "
                        + "and a.tenantId = :tenantId and a.userId = :userId", Attachment.class)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", userId)
                .getResultStream().findFirst();
    }

    @Override
    public List<Attachment> findExpiredChatAttachments(Instant lastUsedBefore, UUID afterId, int limit) {
        if (lastUsedBefore == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("Invalid cleanup page request");
        }
        return entityManager.createQuery("select a from Attachment a where a.purpose = :purpose "
                        + "and a.cleanupCompletedAt is null "
                        + "and (a.status = :expired or a.updatedAt <= :lastUsedBefore) "
                        + "and (:afterId is null or a.id > :afterId) order by a.id", Attachment.class)
                .setParameter("purpose", AttachmentPurpose.CHAT_ATTACHMENT)
                .setParameter("expired", AttachmentStatus.EXPIRED)
                .setParameter("lastUsedBefore", lastUsedBefore)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }
}
