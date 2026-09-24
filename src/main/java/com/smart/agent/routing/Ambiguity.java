package com.smart.agent.routing;

public record Ambiguity(String type, String question, java.util.List<QueryIntentCandidate> candidates) {
    public Ambiguity {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("ambiguity type must not be blank");
        question = question == null ? "" : question;
        candidates = candidates == null ? java.util.List.of() : java.util.List.copyOf(candidates);
    }
}
