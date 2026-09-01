package com.smart.agent.security;

import java.util.Set;

public record AgentUserContext(
        String tenantId,
        String userId,
        String identityId,
        Set<String> permissions,
        Set<String> projectIds) {
    public AgentUserContext {
        tenantId = requireText(tenantId, "tenantId");
        userId = requireText(userId, "userId");
        identityId = requireText(identityId, "identityId");
        permissions = Set.copyOf(permissions);
        projectIds = Set.copyOf(projectIds);
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
