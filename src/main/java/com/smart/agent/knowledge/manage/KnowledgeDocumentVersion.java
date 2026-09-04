package com.smart.agent.knowledge.manage;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentVersion(UUID id, UUID documentId, UUID attachmentId, String tenantId,
        int versionNo, KnowledgeStatus status, String failureCode, String createdBy,
        Instant createdAt, String updatedBy, Instant updatedAt) {

    public KnowledgeDocumentVersion(UUID id, UUID documentId, UUID attachmentId, String tenantId,
            int versionNo, KnowledgeStatus status, String failureCode, String createdBy,
            Instant createdAt, Instant updatedAt) {
        this(id, documentId, attachmentId, tenantId, versionNo, status, failureCode,
                createdBy, createdAt, createdBy, updatedAt);
    }

    public KnowledgeDocumentVersion withStatus(KnowledgeStatus next, String code, String actor, Instant now) {
        return new KnowledgeDocumentVersion(id, documentId, attachmentId, tenantId, versionNo,
                next, code, createdBy, createdAt, actor, now);
    }
}
