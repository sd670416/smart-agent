package com.smart.agent.model.config;

import java.time.Instant;
import java.util.Set;

/**
 * 模型配置管理端响应（脱敏）。
 *
 * <p>只暴露 {@code hasApiKey} 与掩码 {@code apiKeyMask}，永远不返回明文密钥或密文。</p>
 */
public record ModelConfigResponse(
        String id,
        String name,
        ModelProviderType providerType,
        ModelDeploymentType deploymentType,
        String baseUrl,
        String modelName,
        boolean hasApiKey,
        String apiKeyMask,
        Set<ModelCapability> capabilities,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        boolean enabled,
        boolean defaultModel,
        int sort,
        String remarks,
        long configVersion,
        String lastTestStatus,
        Instant lastTestTime,
        String lastTestSummary,
        Instant createdAt,
        Instant updatedAt) {

    /** 供普通聊天用户使用的已启用模型摘要，只包含选择模型所需的最小信息。 */
    public record Summary(
            String id,
            String name,
            ModelDeploymentType deploymentType,
            Set<ModelCapability> capabilities,
            boolean defaultModel) {
    }
}
