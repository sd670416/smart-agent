package com.smart.agent.security;

import java.util.Set;

public record AgentUserContext(
        String tenantId,
        String userId,
        String identityId,
        Set<String> permissions,
        Set<String> projectIds,
        Set<String> knowledgeSpaceIds) {
    public AgentUserContext {
        tenantId = requireText(tenantId, "tenantId");
        userId = requireText(userId, "userId");
        identityId = requireText(identityId, "identityId");
        permissions = Set.copyOf(permissions);
        projectIds = Set.copyOf(projectIds);
        knowledgeSpaceIds = Set.copyOf(knowledgeSpaceIds);
    }

    public AgentUserContext(
            String tenantId, String userId, String identityId, Set<String> permissions, Set<String> projectIds) {
        this(tenantId, userId, identityId, permissions, projectIds, Set.of());
    }

    public boolean canAccessProject(String projectId) {
        return projectIds.contains(projectId);
    }

    public boolean canAccessKnowledgeSpace(String spaceId) {
        return knowledgeSpaceIds.contains(spaceId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
