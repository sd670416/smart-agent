package com.smart.agent.knowledge.manage;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSpace(UUID id, String tenantId, String name, String description,
        KnowledgeScope scope, String projectId, KnowledgeStatus status, String createdBy,
        Instant createdAt, String updatedBy, Instant updatedAt) {

    public KnowledgeSpace {
        if (id == null) throw new IllegalArgumentException("id is required");
        tenantId = required(tenantId, "tenantId");
        name = required(name, "name");
        if (scope == null) throw new IllegalArgumentException("scope is required");
        if (status == null) throw new IllegalArgumentException("status is required");
    }

    public KnowledgeSpace withDetails(String nextName, String nextDescription, String actor, Instant now) {
        return new KnowledgeSpace(id, tenantId, nextName, nextDescription, scope, projectId, status,
                createdBy, createdAt, actor, now);
    }

    public KnowledgeSpace withStatus(KnowledgeStatus next, String actor, Instant now) {
        return new KnowledgeSpace(id, tenantId, name, description, scope, projectId, next,
                createdBy, createdAt, actor, now);
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
