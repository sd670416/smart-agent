package com.smart.agent.knowledge;

import java.util.List;

public record IndexedChunk(
        String chunkId,
        String documentId,
        String tenantId,
        String spaceId,
        String projectId,
        String status,
        String content,
        List<Float> vector) {

    /** Payload value used for documents that apply to every project in their knowledge space. */
    public static final String GLOBAL_PROJECT_ID = "__global__";

    public IndexedChunk {
        chunkId = requireText(chunkId, "chunkId");
        documentId = requireText(documentId, "documentId");
        tenantId = requireText(tenantId, "tenantId");
        spaceId = requireText(spaceId, "spaceId");
        projectId = optionalText(projectId, "projectId");
        status = requireText(status, "status");
        content = requireText(content, "content");
        if (vector == null || vector.isEmpty() || vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException("vector must contain finite values");
        }
        vector = List.copyOf(vector);
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
