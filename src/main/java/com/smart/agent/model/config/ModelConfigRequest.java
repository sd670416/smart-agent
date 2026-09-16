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

    /**
     * 显式覆盖记录类型的默认 {@code toString()}。
     *
     * <p>默认实现会把明文 {@code apiKey} 拼进字符串，一旦该请求对象被打进日志、
     * 断点或异常信息就是明文泄露。这里固定输出 {@code ****}。</p>
     */
    @Override
    public String toString() {
        return "ModelConfigRequest[name=" + name
                + ", deploymentType=" + deploymentType
                + ", baseUrl=" + baseUrl
                + ", modelName=" + modelName
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "null" : "****")
                + ", capabilities=" + capabilities
                + ", connectTimeoutSeconds=" + connectTimeoutSeconds
                + ", readTimeoutSeconds=" + readTimeoutSeconds
                + ", sort=" + sort
                + ", remarks=" + remarks
                + "]";
    }
}
