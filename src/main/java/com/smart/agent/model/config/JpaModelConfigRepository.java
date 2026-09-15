package com.smart.agent.model.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaModelConfigRepository implements ModelConfigRepository {
    private static final Logger log = LoggerFactory.getLogger(JpaModelConfigRepository.class);

    private final EntityManager entityManager;
    private volatile Boolean modelBindingColumnsPresent;

    public JpaModelConfigRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override public ModelConfig save(ModelConfig config) {
        if (entityManager.contains(config)) return config;
        if (entityManager.find(ModelConfig.class, config.id()) == null) {
            entityManager.persist(config);
            return config;
        }
        return entityManager.merge(config);
    }

    // 统一不用 getResultStream()：Hibernate 6 下它返回的流由 JDBC ResultSet 直接支撑，
    // findFirst() 短路后流不会关闭，ResultSet 悬挂会让同一事务内后续语句报
    // "Operation not allowed after ResultSet closed"。这两个查询都是「取唯一一条」，
    // setMaxResults(1) + getResultList() 语义等价且资源被完整读完并释放。
    @Override public Optional<ModelConfig> findById(String id) {
        return entityManager.createQuery("select m from ModelConfig m where m.id = :id and m.deleted = false", ModelConfig.class)
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override public Optional<ModelConfig> findDefault() {
        return entityManager.createQuery("select m from ModelConfig m where m.deleted = false and m.defaultModel = true", ModelConfig.class)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override public List<ModelConfig> findEnabled() {
        return entityManager.createQuery("select m from ModelConfig m where m.deleted = false and m.enabled = true order by m.sort, m.createdAt", ModelConfig.class)
                .getResultList();
    }

    @Override public List<ModelConfig> findAllActive() {
        return entityManager.createQuery("select m from ModelConfig m where m.deleted = false order by m.sort, m.createdAt", ModelConfig.class)
                .getResultList();
    }

    @Override public List<ModelConfig> findPage(ModelConfigQuery query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        TypedQuery<ModelConfig> typed = entityManager.createQuery(
                "select m from ModelConfig m" + filterClause(query, parameters) + " order by m.sort, m.createdAt",
                ModelConfig.class);
        parameters.forEach(typed::setParameter);
        return typed.setFirstResult(query.offset()).setMaxResults(query.size()).getResultList();
    }

    @Override public long count(ModelConfigQuery query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        TypedQuery<Long> typed = entityManager.createQuery(
                "select count(m) from ModelConfig m" + filterClause(query, parameters), Long.class);
        parameters.forEach(typed::setParameter);
        return typed.getSingleResult();
    }

    @Override public void flush() {
        entityManager.flush();
    }

    /**
     * 统计引用该模型的会话与运行记录数。
     *
     * <p>模型绑定列（{@code ai_conversation.model_id}、{@code ai_run.model_id}）由 V12 迁移增加。
     * 在绑定功能上线前，会话和运行事实上不可能引用任何模型，因此列缺失时引用数按 0 处理。
     * 该判断只读元数据并缓存结果，避免在删除检查时抛出未知列错误。</p>
     */
    @Override public long countReferences(String modelId) {
        if (!modelBindingColumnsPresent()) {
            return 0L;
        }
        Number conversations = (Number) entityManager.createNativeQuery("select count(*) from ai_conversation where model_id = :modelId")
                .setParameter("modelId", modelId).getSingleResult();
        Number runs = (Number) entityManager.createNativeQuery("select count(*) from ai_run where model_id = :modelId")
                .setParameter("modelId", modelId).getSingleResult();
        return conversations.longValue() + runs.longValue();
    }

    private static String filterClause(ModelConfigQuery query, Map<String, Object> parameters) {
        StringBuilder clause = new StringBuilder(" where m.deleted = false");
        if (query.name() != null) {
            clause.append(" and lower(m.name) like :name");
            parameters.put("name", "%" + query.name().toLowerCase(Locale.ROOT) + "%");
        }
        if (query.deploymentType() != null) {
            clause.append(" and m.deploymentType = :deploymentType");
            parameters.put("deploymentType", query.deploymentType());
        }
        if (query.enabled() != null) {
            clause.append(" and m.enabled = :enabled");
            parameters.put("enabled", query.enabled());
        }
        if (query.defaultModel() != null) {
            clause.append(" and m.defaultModel = :defaultModel");
            parameters.put("defaultModel", query.defaultModel());
        }
        return clause.toString();
    }

    private boolean modelBindingColumnsPresent() {
        Boolean cached = modelBindingColumnsPresent;
        if (cached != null) {
            return cached;
        }
        Number columns = (Number) entityManager.createNativeQuery(
                        "select count(*) from information_schema.columns where table_schema = database() "
                                + "and table_name in ('ai_conversation', 'ai_run') and column_name = 'model_id'")
                .getSingleResult();
        boolean present = columns.longValue() >= 2;
        modelBindingColumnsPresent = present;
        if (!present) {
            log.info("会话与运行记录尚未绑定模型列，模型引用检查暂按 0 处理，待 V12 迁移后自动生效");
        }
        return present;
    }
}
