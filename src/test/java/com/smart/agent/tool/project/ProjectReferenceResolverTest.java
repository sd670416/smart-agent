package com.smart.agent.tool.project;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectReferenceResolverTest {
    private final ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("p1", "p2"));

    @Test void trustedIdWithAnotherReferenceQueriesOnlyThatId() {
        var client = new LocalProjectBusinessClient() {
            @Override public ProjectQueryResult query(ToolContext scope, ProjectQueryInput input) {
                assertThat(scope.projectIds()).containsExactly("p1");
                assertThat(input.select()).containsExactly("projectCode", "projectName");
                return new ProjectQueryResult("detail", 1, 6, 1L, false, List.of(),
                        List.of(Map.of("projectId", "p1", "projectCode", "001", "projectName", "same")), List.of());
            }
        };
        assertThat(new ProjectReferenceResolver(client).resolve(context, "p1", null, "same"))
                .extracting(ProjectReferenceResolver.Reference::projectId).containsExactly("p1");
    }

    @Test void conflictingTrustedIdCannotMatchAnotherProjectsCode() {
        var client = returning(List.of(Map.of("projectId", "p2", "projectCode", "p1", "projectName", "other")), 1, false);
        assertThatThrownBy(() -> new ProjectReferenceResolver(client).resolve(context, "p1", null, "other"))
                .isInstanceOf(AgentException.class);
    }

    @Test void wrongBackendScopeIsRejected() {
        var client = returning(List.of(Map.of("projectId", "hidden", "projectCode", "001", "projectName", "same")), 1, false);
        assertThatThrownBy(() -> new ProjectReferenceResolver(client).resolve(context, null, "001", null))
                .isInstanceOf(AgentException.class).extracting("code").isEqualTo("AGENT_TOOL_PROJECT_FORBIDDEN");
    }

    @Test void fuzzyBackendResultCannotBeSelectedAsExactName() {
        var client = returning(List.of(Map.of("projectId", "p1", "projectCode", "001", "projectName", "same-other")), 1, false);
        assertThatThrownBy(() -> new ProjectReferenceResolver(client).resolve(context, null, null, "same"))
                .isInstanceOf(AgentException.class).extracting("code").isEqualTo("AGENT_PROJECT_REFERENCE_NOT_FOUND");
    }

    @Test void truncatedSingleMatchCannotHideAmbiguity() {
        var client = returning(List.of(Map.of("projectId", "p1", "projectCode", "001", "projectName", "same")), 10, true);
        assertThatThrownBy(() -> new ProjectReferenceResolver(client).resolve(context, null, null, "same"))
                .isInstanceOf(AgentException.class).extracting("code").isEqualTo("AGENT_PROJECT_REFERENCE_AMBIGUOUS");
    }

    private LocalProjectBusinessClient returning(List<Map<String, Object>> rows, long total, boolean next) {
        return new LocalProjectBusinessClient() {
            @Override public ProjectQueryResult query(ToolContext scope, ProjectQueryInput input) {
                return new ProjectQueryResult("detail", 1, 6, total, next, List.of(), rows, List.of());
            }
        };
    }
}
