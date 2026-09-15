package com.smart.agent.tool;

import com.smart.agent.security.AgentUserContext;
import java.util.Optional;
import java.util.Set;

/**
 * 工具执行上下文。
 *
 * <p>{@code modelBinding} 是本轮运行固定的模型绑定，由编排层在取得执行句柄后写入，
 * 联网搜索等依赖模型的工具据此使用"本次会话所选模型"，而不是全局 YAML 配置。
 * 该字段不参与权限判断，也不包含 API Key。
 */
public record ToolContext(
        String tenantId,
        String userId,
        String identityId,
        Set<String> roleIds,
        Set<String> projectIds,
        Set<String> permissions,
        ModelBinding modelBinding) {

    /**
     * 本轮运行绑定的模型信息：连接地址、实际模型名与能力集合。
     * 刻意不携带 API Key——密钥只在提供方内部按模型 ID 换取，避免随上下文流转。
     */
    public record ModelBinding(
            String modelId,
            String displayName,
            String modelName,
            String baseUrl,
            Set<com.smart.agent.model.config.ModelCapability> capabilities) {
        public ModelBinding {
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }

        /**
         * 是否具备指定能力。未声明能力集合时按"不支持"处理，
         * 避免模型被误用于它没有的能力。
         */
        public boolean supports(com.smart.agent.model.config.ModelCapability capability) {
            return capabilities.contains(capability);
        }

        /** 是否具备联网搜索能力。 */
        public boolean supportsWebSearch() {
            return supports(com.smart.agent.model.config.ModelCapability.WEB_SEARCH);
        }
    }

    public ToolContext {
        tenantId = requireText(tenantId, "tenantId");
        userId = requireText(userId, "userId");
        identityId = requireText(identityId, "identityId");
        roleIds = Set.copyOf(roleIds);
        projectIds = Set.copyOf(projectIds);
        permissions = Set.copyOf(permissions);
    }

    public ToolContext(String tenantId, String userId, String identityId, Set<String> projectIds) {
        this(tenantId, userId, identityId, Set.of(), projectIds, Set.of(), null);
    }

    public ToolContext(String tenantId, String userId, String identityId, Set<String> roleIds,
            Set<String> projectIds, Set<String> permissions) {
        this(tenantId, userId, identityId, roleIds, projectIds, permissions, null);
    }

    public static ToolContext from(AgentUserContext userContext) {
        return new ToolContext(
                userContext.tenantId(), userContext.userId(), userContext.identityId(), userContext.roleIds(),
                userContext.projectIds(), userContext.permissions(), null);
    }

    /** 附加本轮模型绑定的副本。 */
    public ToolContext withModelBinding(ModelBinding binding) {
        return new ToolContext(tenantId, userId, identityId, roleIds, projectIds, permissions, binding);
    }

    public Optional<ModelBinding> modelBindingOptional() {
        return Optional.ofNullable(modelBinding);
    }

    public boolean canAccessProject(String projectId) {
        return projectIds.contains(projectId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
