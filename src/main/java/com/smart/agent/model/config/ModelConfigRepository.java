package com.smart.agent.model.config;

import java.util.List;
import java.util.Optional;

/**
 * 模型配置仓储。
 *
 * <p>模型配置为全局配置，不按租户隔离；所有查询都排除已逻辑删除记录。</p>
 */
public interface ModelConfigRepository {

    ModelConfig save(ModelConfig config);

    Optional<ModelConfig> findById(String id);

    Optional<ModelConfig> findDefault();

    List<ModelConfig> findEnabled();

    List<ModelConfig> findAllActive();

    List<ModelConfig> findPage(ModelConfigQuery query);

    long count(ModelConfigQuery query);

    long countReferences(String modelId);

    /** 立即把挂起的变更刷到数据库，用于默认模型切换时先释放唯一默认槽位。 */
    void flush();
}
