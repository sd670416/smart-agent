package com.smart.agent.knowledge;

import java.util.List;

public record KnowledgeDocument(
        String id,
        String versionId,
        String tenantId,
        String spaceId,
        String organizationId,
        String projectId,
        String title,
        String status,
        String sourceText,
        String sourceChecksum,
        String parserVersion,
        String embeddingModelKey,
        String actorId,
        List<KnowledgeChunk> chunks) {

    public KnowledgeDocument {
        id = requireText(id, "id");
        versionId = requireText(versionId, "versionId");
        tenantId = requireText(tenantId, "tenantId");
        spaceId = requireText(spaceId, "spaceId");
        title = requireText(title, "title");
        status = requireText(status, "status");
        sourceText = requireText(sourceText, "sourceText");
        sourceChecksum = requireText(sourceChecksum, "sourceChecksum");
        parserVersion = requireText(parserVersion, "parserVersion");
        embeddingModelKey = requireText(embeddingModelKey, "embeddingModelKey");
        actorId = requireText(actorId, "actorId");
        chunks = List.copyOf(chunks);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
