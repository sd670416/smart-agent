package com.smart.agent.knowledge;

import java.time.Instant;

public record IngestDocumentCommand(
        String tenantId,
        String spaceId,
        String organizationId,
        String projectId,
        String documentId,
        String documentVersionId,
        String attachmentId,
        String title,
        String status,
        String parserVersion,
        String actorId,
        Instant expiresAt) {

    public IngestDocumentCommand {
        tenantId = required(tenantId, "tenantId");
        spaceId = required(spaceId, "spaceId");
        documentId = required(documentId, "documentId");
        documentVersionId = required(documentVersionId, "documentVersionId");
        attachmentId = required(attachmentId, "attachmentId");
        title = required(title, "title");
        status = required(status, "status").toLowerCase(java.util.Locale.ROOT);
        parserVersion = required(parserVersion, "parserVersion");
        actorId = required(actorId, "actorId");
        organizationId = optional(organizationId, "organizationId");
        projectId = optional(projectId, "projectId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static String optional(String value, String field) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(field + " must be null or non-blank");
        return value;
    }
}
