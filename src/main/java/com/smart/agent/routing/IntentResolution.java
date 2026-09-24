package com.smart.agent.routing;

import java.util.List;
import java.util.Optional;

public final class IntentResolution {
    public enum Status { RESOLVED, NEEDS_CLARIFICATION, UNKNOWN }

    private final Status status;
    private final QueryIntentCandidate candidate;
    private final Ambiguity ambiguity;

    private IntentResolution(Status status, QueryIntentCandidate candidate, Ambiguity ambiguity) {
        this.status = status;
        this.candidate = candidate;
        this.ambiguity = ambiguity;
    }

    public static IntentResolution resolved(QueryIntentCandidate candidate) {
        return new IntentResolution(Status.RESOLVED, candidate, null);
    }

    public static IntentResolution needsClarification(String question, List<QueryIntentCandidate> candidates) {
        return new IntentResolution(Status.NEEDS_CLARIFICATION, null,
                new Ambiguity("BUSINESS_DOMAIN", question, candidates));
    }

    public static IntentResolution unknown(String question) {
        return new IntentResolution(Status.UNKNOWN, null,
                new Ambiguity("BUSINESS_DOMAIN", question, List.of()));
    }

    public Status status() { return status; }
    public Optional<QueryIntentCandidate> candidate() { return Optional.ofNullable(candidate); }
    public Optional<Ambiguity> ambiguity() { return Optional.ofNullable(ambiguity); }
}
