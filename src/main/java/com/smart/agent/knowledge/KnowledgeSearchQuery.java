package com.smart.agent.knowledge;

import java.util.Set;

public record KnowledgeSearchQuery(String query, Set<String> allowedSpaceIds, String projectId, int topK) {
    public KnowledgeSearchQuery {
        query = requireText(query, "query");
        projectId = requireText(projectId, "projectId");
        if (IndexedChunk.GLOBAL_PROJECT_ID.equals(projectId)) {
            throw new IllegalArgumentException("projectId uses a reserved global sentinel");
        }
        if (allowedSpaceIds == null || allowedSpaceIds.isEmpty()
                || allowedSpaceIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("allowedSpaceIds must contain at least one non-blank value");
        }
        allowedSpaceIds = Set.copyOf(allowedSpaceIds);
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
