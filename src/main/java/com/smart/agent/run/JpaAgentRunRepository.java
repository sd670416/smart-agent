package com.smart.agent.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(EntityManagerFactory.class)
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
}
