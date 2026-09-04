package com.smart.agent.knowledge;

import java.util.List;
import java.time.Instant;

public record IndexedChunk(
        String chunkId,
        String documentId,
        String documentVersionId,
        String tenantId,
        String spaceId,
        String projectId,
        String status,
        String attachmentId,
        Instant expiresAt,
        Integer pageNumber,
        String sheetName,
        String sectionTitle,
        String content,
        List<Float> vector) {

    /** Payload value used for documents that apply to every project in their knowledge space. */
    public static final String GLOBAL_PROJECT_ID = "__global__";

    public IndexedChunk {
        chunkId = requireText(chunkId, "chunkId");
        documentId = requireText(documentId, "documentId");
        documentVersionId = requireText(documentVersionId, "documentVersionId");
        tenantId = requireText(tenantId, "tenantId");
        spaceId = requireText(spaceId, "spaceId");
        projectId = optionalText(projectId, "projectId");
        status = requireText(status, "status");
        attachmentId = optionalText(attachmentId, "attachmentId");
        if (pageNumber != null && pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        sheetName = optionalText(sheetName, "sheetName");
        sectionTitle = optionalText(sectionTitle, "sectionTitle");
        content = requireText(content, "content");
        if (vector == null || vector.isEmpty() || vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException("vector must contain finite values");
        }
        vector = List.copyOf(vector);
    }

    public IndexedChunk(String chunkId, String documentId, String tenantId, String spaceId,
            String projectId, String status, String content, List<Float> vector) {
        this(chunkId, documentId, documentId, tenantId, spaceId, projectId, status,
                null, null, null, null, null, content, vector);
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
