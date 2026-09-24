package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;

/** Extension point for adding a business domain without changing the central orchestrator. */
public interface BusinessDomainContributor {
    BusinessDomainDescriptor descriptor();

    /**
     * Applies coarse availability filtering only. Tool-level authorization remains in the trusted tool path.
     */
    default boolean availableTo(AgentUserContext context) {
        return context != null;
    }
}
