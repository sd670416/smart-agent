package com.smart.agent.knowledge;

import java.util.Set;

public record IngestTextCommand(
        String tenantId,
        String spaceId,
        String organizationId,
        String projectId,
        String title,
        String text,
        String status,
        String actorId) {

    private static final Set<String> ALLOWED_STATUSES = Set.of("draft", "published");

    public IngestTextCommand {
        tenantId = requireText(tenantId, "tenantId");
        spaceId = requireText(spaceId, "spaceId");
        organizationId = optionalText(organizationId, "organizationId");
        projectId = optionalText(projectId, "projectId");
        if (IndexedChunk.GLOBAL_PROJECT_ID.equals(projectId)) {
            throw new IllegalArgumentException("projectId uses a reserved global sentinel");
        }
        title = requireText(title, "title");
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        status = requireText(status, "status").toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be draft or published");
        }
        actorId = requireText(actorId, "actorId");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be null or non-blank");
        }
        return value;
    }
}
