package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Deterministic final裁决 after model or keyword candidate generation. */
@Component
public class QueryIntentResolver {
    private final BusinessDomainRegistry registry;

    public QueryIntentResolver(BusinessDomainRegistry registry) {
        this.registry = registry;
    }

    public IntentResolution resolve(String question, Map<String, ?> pageContext,
            String activeDomain, AgentUserContext userContext) {
        String normalized = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return IntentResolution.unknown(question);
        List<QueryIntentCandidate> candidates = new ArrayList<>();
        for (BusinessDomainDescriptor domain : registry.availableDomains(userContext)) {
            int score = score(domain, normalized, pageContext, activeDomain);
            if (score > 0) candidates.add(new QueryIntentCandidate(domain, reason(domain, score), score));
        }
        candidates.sort(Comparator.comparingInt(QueryIntentCandidate::score).reversed());
        if (candidates.isEmpty()) return IntentResolution.unknown(question);
        if (candidates.size() == 1 || candidates.get(0).score() > candidates.get(1).score()) {
            return IntentResolution.resolved(candidates.get(0));
        }
        return IntentResolution.needsClarification(question, candidates);
    }

    /** Returns the registered domain for a tool without exposing the registry internals. */
    public String domainCodeForTool(String toolKey) {
        return registry.findByTool(toolKey).map(BusinessDomainDescriptor::code).orElse(null);
    }

    private int score(BusinessDomainDescriptor domain, String question,
            Map<String, ?> pageContext, String activeDomain) {
        int score = 0;
        for (String term : domain.triggerTerms()) {
            if (term != null && !term.isBlank() && question.contains(term.toLowerCase(Locale.ROOT))) score += 10;
        }
        if (domain.code().equalsIgnoreCase(activeDomain)) score += 3;
        if (pageContext != null) {
            String page = String.valueOf(pageContext.get("routeName")) + " "
                    + String.valueOf(pageContext.get("routePath"));
            page = page.toLowerCase(Locale.ROOT);
            for (String term : domain.triggerTerms()) {
                if (term != null && page.contains(term)) score += 2;
            }
        }
        return score;
    }

    private String reason(BusinessDomainDescriptor domain, int score) {
        return "匹配业务域关键词或当前会话上下文，评分=" + score;
    }
}
