package com.smart.agent.knowledge;

public record KnowledgeChunk(
        String id,
        String documentId,
        String documentVersionId,
        String tenantId,
        int ordinal,
        String content,
        String checksum,
        String vectorPointId,
        Integer pageNumber,
        String sectionTitle) {

    public KnowledgeChunk {
        id = requireText(id, "id");
        documentId = requireText(documentId, "documentId");
        documentVersionId = requireText(documentVersionId, "documentVersionId");
        tenantId = requireText(tenantId, "tenantId");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        content = requireText(content, "content");
        checksum = requireText(checksum, "checksum");
        vectorPointId = requireText(vectorPointId, "vectorPointId");
        if (pageNumber != null && pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (sectionTitle != null && sectionTitle.isBlank()) {
            sectionTitle = null;
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
