package com.smart.agent.model.config;

/**
 * 模型配置分页查询条件。
 *
 * <p>分页参数在此处统一收敛边界，避免仓储层出现负数偏移或超大页容量。</p>
 */
public record ModelConfigQuery(
        String name,
        ModelDeploymentType deploymentType,
        Boolean enabled,
        Boolean defaultModel,
        int page,
        int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public ModelConfigQuery {
        name = name == null || name.isBlank() ? null : name.trim();
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
    }

    public int offset() {
        return page * size;
    }
}
