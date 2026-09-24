package com.smart.agent.routing;

/** A program-checkable candidate produced from the user's question and context. */
public record QueryIntentCandidate(BusinessDomainDescriptor domain, String reason, int score) {
    public QueryIntentCandidate {
        if (domain == null) throw new IllegalArgumentException("candidate domain must not be null");
        reason = reason == null ? "" : reason;
    }
}
