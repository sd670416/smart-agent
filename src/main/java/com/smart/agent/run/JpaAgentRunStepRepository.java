package com.smart.agent.run;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaAgentRunStepRepository implements AgentRunStepRepository {
    private final EntityManager entityManager;
    public JpaAgentRunStepRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    @Override @Transactional
    public AgentRunStep save(AgentRunStep step) { entityManager.persist(step); return step; }
    @Override
    public List<AgentRunStep> findByTenantIdAndUserIdAndRunIdOrderBySequence(
            String tenantId, String userId, String runId) {
        return entityManager.createQuery("select s from AgentRunStep s where s.tenantId=:tenant and s.userId=:user "
                + "and s.runId=:run order by s.sequence", AgentRunStep.class)
                .setParameter("tenant", tenantId).setParameter("user", userId).setParameter("run", runId)
                .getResultList();
    }
}
