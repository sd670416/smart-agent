package com.smart.agent.tool;

import com.smart.agent.security.AgentUserContext;
import java.util.Set;

public record ToolContext(String tenantId, String userId, String identityId, Set<String> projectIds) {
    public ToolContext {
        tenantId = requireText(tenantId, "tenantId");
        userId = requireText(userId, "userId");
        identityId = requireText(identityId, "identityId");
        projectIds = Set.copyOf(projectIds);
    }

    public static ToolContext from(AgentUserContext userContext) {
        return new ToolContext(
                userContext.tenantId(), userContext.userId(), userContext.identityId(), userContext.projectIds());
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
