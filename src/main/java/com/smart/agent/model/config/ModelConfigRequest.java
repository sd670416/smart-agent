package com.smart.agent.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

/**
 * 模型配置新增与修改请求。
 *
 * <p>{@code apiKey} 为明文密钥：新增时直接写入数据库；修改时留空表示保留原有密钥，
 * 不会把已配置的密钥清空。接口不会回显该字段。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ModelConfigRequest(
        String name,
        ModelDeploymentType deploymentType,
        String baseUrl,
        String modelName,
        String apiKey,
        Set<ModelCapability> capabilities,
        Integer connectTimeoutSeconds,
        Integer readTimeoutSeconds,
        Integer sort,
        String remarks) {
}
