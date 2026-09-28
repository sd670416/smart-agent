package com.smart.agent.clarification;

import com.smart.agent.routing.Ambiguity;
import com.smart.agent.routing.IntentResolution;
import com.smart.agent.routing.QueryIntentCandidate;
import java.util.List;

/** Safe, UI-facing representation of an ambiguity resolution request. */
public record ClarificationPrompt(String question, String ambiguityType, List<Option> options) {
    public record Option(String id, String label, String domainCode) {}

    public ClarificationPrompt {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("question must not be blank");
        if (ambiguityType == null || ambiguityType.isBlank()) {
            throw new IllegalArgumentException("ambiguityType must not be blank");
        }
        options = options == null ? List.of() : List.copyOf(options);
        if (options.size() < 2) throw new IllegalArgumentException("clarification needs at least two options");
    }

    public static ClarificationPrompt from(IntentResolution resolution) {
        if (resolution == null || resolution.status() != IntentResolution.Status.NEEDS_CLARIFICATION) {
            throw new IllegalArgumentException("resolution does not need clarification");
        }
        Ambiguity ambiguity = resolution.ambiguity().orElseThrow();
        List<Option> options = ambiguity.candidates().stream()
                .map(QueryIntentCandidate::domain)
                .map(domain -> new Option("domain:" + domain.code(), domain.name(), domain.code()))
                .distinct()
                .limit(5)
                .toList();
        return new ClarificationPrompt("请确认您要查询的业务范围：", ambiguity.type(), options);
    }

    public String displayText() {
        StringBuilder text = new StringBuilder(question);
        for (Option option : options) {
            text.append("\n\n**").append(option.label()).append("**");
        }
        return text.toString();
    }
}
