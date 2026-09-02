package com.smart.agent.knowledge;

/** Trusted citation fields loaded from the metadata store, never from vector payloads. */
public record KnowledgeChunkMetadata(
        String chunkId,
        String documentId,
        String versionId,
        String tenantId,
        String spaceId,
        String projectId,
        String title,
        Integer pageNumber,
        String sectionTitle,
        String content,
        boolean published,
        boolean deleted) {
    public KnowledgeChunkMetadata {
        chunkId = requireText(chunkId, "chunkId");
        documentId = requireText(documentId, "documentId");
        versionId = requireText(versionId, "versionId");
        tenantId = requireText(tenantId, "tenantId");
        spaceId = requireText(spaceId, "spaceId");
        title = requireText(title, "title");
        content = requireText(content, "content");
        if (projectId != null && projectId.isBlank()) {
            projectId = null;
        }
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
