package com.smart.agent.knowledge;

import java.util.List;
import java.util.Set;

public record VectorSearchQuery(
        String tenantId,
        Set<String> allowedSpaceIds,
        Set<String> allowedProjectIds,
        List<Float> vector,
        int topK) {

    public VectorSearchQuery {
        tenantId = requireText(tenantId, "tenantId");
        allowedSpaceIds = validatedScopes(allowedSpaceIds, "allowedSpaceIds");
        allowedProjectIds = validatedScopes(allowedProjectIds, "allowedProjectIds");
        if (allowedSpaceIds.isEmpty() && allowedProjectIds.isEmpty()) {
            throw new IllegalArgumentException("at least one allowed scope is required");
        }
        if (vector == null || vector.isEmpty() || vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException("vector must contain finite values");
        }
        vector = List.copyOf(vector);
        if (topK < 1 || topK > 100) {
            throw new IllegalArgumentException("topK must be between 1 and 100");
        }
    }

    private static Set<String> validatedScopes(Set<String> scopes, String fieldName) {
        if (scopes == null || scopes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(fieldName + " must contain non-blank values");
        }
        return Set.copyOf(scopes);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
