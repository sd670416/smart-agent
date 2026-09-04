package com.smart.agent.attachment;

import jakarta.persistence.EntityManager;
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
}
