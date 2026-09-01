package com.smart.agent.security;

import java.util.Optional;

public final class AgentContextHolder {
    private static final ThreadLocal<AgentUserContext> CONTEXT = new ThreadLocal<>();

    private AgentContextHolder() {
    }

    public static Optional<AgentUserContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static AgentUserContext requireContext() {
        return current().orElseThrow(() -> new IllegalStateException("Agent user context is not available"));
    }

    static void set(AgentUserContext context) {
        CONTEXT.set(context);
    }

    static void clear() {
        CONTEXT.remove();
    }
}
