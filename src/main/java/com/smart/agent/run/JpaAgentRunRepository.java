package com.smart.agent.run;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaAgentRunRepository implements AgentRunRepository {

    private final EntityManager entityManager;

    public JpaAgentRunRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public AgentRun save(AgentRun run) {
        if (entityManager.contains(run)) {
            return run;
        }
        if (entityManager.find(AgentRun.class, run.id()) == null) {
            entityManager.persist(run);
            return run;
        }
        return entityManager.merge(run);
    }

    @Override
    public Optional<AgentRun> findById(String id) {
        return Optional.ofNullable(entityManager.find(AgentRun.class, id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentRun> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id) {
        // 必须用 getResultList() 而不是 getResultStream()：
        // Hibernate 6 的 getResultStream() 返回的流由 JDBC ResultSet 直接支撑，
        // findFirst() 短路后流不会被关闭，ResultSet 会一直悬着，
        // 同一事务内后续的语句（如 flush 时的 UPDATE）就会撞上
        // "Operation not allowed after ResultSet closed"。
        return entityManager.createQuery("select r from AgentRun r where r.id = :id and r.tenantId = :tenantId and r.userId = :userId",
                        AgentRun.class)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRun> findByTenantIdAndUserIdAndConversationId(
            String tenantId, String userId, String conversationId) {
        return entityManager.createQuery(
                        "select r from AgentRun r where r.tenantId = :tenantId and r.userId = :userId "
                                + "and r.conversationId = :conversationId order by r.createdAt asc", AgentRun.class)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", userId)
                .setParameter("conversationId", conversationId)
                .getResultList();
    }
}
