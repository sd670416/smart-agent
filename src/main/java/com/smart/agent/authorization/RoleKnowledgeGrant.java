package com.smart.agent.authorization;

import java.util.UUID;

public record RoleKnowledgeGrant(String tenantId, String roleId, UUID knowledgeSpaceId) {
    public RoleKnowledgeGrant {
        if (tenantId == null || tenantId.isBlank() || roleId == null || roleId.isBlank() || knowledgeSpaceId == null)
            throw new IllegalArgumentException("grant fields are required");
    }
}
