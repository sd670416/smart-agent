package com.smart.agent.routing;

import java.util.List;

/** Immutable metadata used by routing and clarification; it contains no permission decision itself. */
public record BusinessDomainDescriptor(
        String code,
        String name,
        List<String> toolKeys,
        List<DataScopeDescriptor> dataScopes,
        String refreshPolicy,
        List<String> triggerTerms) {
    public BusinessDomainDescriptor {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("business domain code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("business domain name must not be blank");
        toolKeys = toolKeys == null ? List.of() : List.copyOf(toolKeys);
        dataScopes = dataScopes == null ? List.of() : List.copyOf(dataScopes);
        refreshPolicy = refreshPolicy == null || refreshPolicy.isBlank() ? "REALTIME" : refreshPolicy;
        triggerTerms = triggerTerms == null ? List.of() : List.copyOf(triggerTerms);
    }

    public BusinessDomainDescriptor(String code, String name, List<String> toolKeys,
            List<DataScopeDescriptor> dataScopes, String refreshPolicy) {
        this(code, name, toolKeys, dataScopes, refreshPolicy, List.of());
    }

    public boolean ownsTool(String toolKey) {
        return toolKey != null && toolKeys.contains(toolKey);
    }
}
