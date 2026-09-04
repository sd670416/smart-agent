package com.smart.agent.knowledge.manage;

import java.time.Instant;
import java.util.UUID;

public record ManagedKnowledgeDocument(UUID id, String tenantId, UUID spaceId, String title,
        KnowledgeStatus status, UUID activeVersionId, String createdBy, Instant createdAt,
        String updatedBy, Instant updatedAt) {

    public ManagedKnowledgeDocument withStatus(KnowledgeStatus next, String actor, Instant now) {
        return new ManagedKnowledgeDocument(id, tenantId, spaceId, title, next, activeVersionId,
                createdBy, createdAt, actor, now);
    }
}
