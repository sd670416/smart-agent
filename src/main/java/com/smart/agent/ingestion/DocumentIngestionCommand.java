package com.smart.agent.ingestion;

import java.time.Instant;
import java.util.UUID;

public record DocumentIngestionCommand(
        UUID attachmentId,
        String tenantId,
        String userId,
        String spaceId,
        String organizationId,
        String projectId,
        String documentId,
        String documentVersionId,
        String title,
        String status,
        Instant expiresAt) {

    public DocumentIngestionCommand {
        if (attachmentId == null) throw new IllegalArgumentException("attachmentId is required");
        tenantId = required(tenantId, "tenantId");
        userId = required(userId, "userId");
        spaceId = required(spaceId, "spaceId");
        documentId = required(documentId, "documentId");
        documentVersionId = required(documentVersionId, "documentVersionId");
        title = required(title, "title");
        status = required(status, "status");
        organizationId = optional(organizationId, "organizationId");
        projectId = optional(projectId, "projectId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String optional(String value, String field) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(field + " must be null or non-blank");
        return value;
    }
}
