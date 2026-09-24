package com.smart.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryIntentResolverTest {
    private final BusinessDomainRegistry registry = new BusinessDomainRegistry(List.of(
            () -> new BusinessDomainDescriptor("PROJECT", "项目", List.of("project.query"), List.of(), "REALTIME",
                    List.of("项目")),
            () -> new BusinessDomainDescriptor("APPROVAL", "审批", List.of("approval.query"), List.of(), "REALTIME",
                    List.of("待办", "审批"))));
    private final QueryIntentResolver resolver = new QueryIntentResolver(registry);
    private final AgentUserContext user = new AgentUserContext("tenant", "user", "identity", Set.of());

    @Test
    void resolvesUniqueDomain() {
        IntentResolution result = resolver.resolve("查一下待办", Map.of(), null, user);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("APPROVAL", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void activeDomainBreaksTieOnlyWhenItIsTheStrongestContext() {
        IntentResolution result = resolver.resolve("查一下项目", Map.of(), "PROJECT", user);

        assertEquals(IntentResolution.Status.RESOLVED, result.status());
        assertEquals("PROJECT", result.candidate().orElseThrow().domain().code());
    }

    @Test
    void equalCandidatesRequireClarification() {
        IntentResolution result = resolver.resolve("统计项目审批", Map.of(), null, user);

        assertEquals(IntentResolution.Status.NEEDS_CLARIFICATION, result.status());
        assertTrue(result.ambiguity().isPresent());
    }
}
