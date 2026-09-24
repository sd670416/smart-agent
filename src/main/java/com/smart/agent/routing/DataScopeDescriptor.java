package com.smart.agent.routing;

import java.util.List;

/** Describes how a business domain obtains its trusted data scope. */
public record DataScopeDescriptor(String code, String name, List<String> capabilities) {
    public DataScopeDescriptor {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("data scope code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("data scope name must not be blank");
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
