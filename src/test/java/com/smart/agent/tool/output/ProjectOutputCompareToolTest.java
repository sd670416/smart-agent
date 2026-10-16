package com.smart.agent.tool.output;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectOutputCompareToolTest {
    @Test void dateChoicesNeverOfferContractDates() {
        var prompt = new ProjectOutputClarifyDateTool().execute(new ProjectOutputClarifyDateTool.Input(),
                new ToolContext("t", "u", "i", Set.of(), Set.of("p1"), Set.of("menu:project")));
        assertThat(prompt.options()).extracting(com.smart.agent.clarification.ClarificationPrompt.Option::id)
                .containsExactlyInAnyOrder("date:projectCreateDate", "date:createDate");
    }
    private ProjectOutputQueryInput group() {
        return new ProjectOutputQueryInput("p1", null, "主体", "detail", "createDate", "2026-09-01", "2026-09-30", 9, 20);
    }
    @Test void resolvesEachGroupAndForcesSummaryInsteadOfComparingPage() {
        ProjectOutputBusinessClient client = new ProjectOutputBusinessClient() {
            public ProjectQueryResult query(ToolContext c, ProjectOutputQueryInput i) { throw new AssertionError(); }
            public ProjectQueryResult compare(ToolContext c, ProjectOutputCompareInput i) {
                assertThat(i.current().mode()).isEqualTo("summary"); assertThat(i.current().page()).isEqualTo(1);
                assertThat(i.current().projectId()).isEqualTo("p1"); assertThat(i.current().outputReportNo()).isEqualTo("主体");
                return new ProjectQueryResult("aggregate", 1, 2, 2L, false, List.of(), List.of(Map.of("difference", 1)), List.of());
            }
        };
        var tool = new ProjectOutputCompareTool(client, new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        var context = new ToolContext("t", "u", "i", Set.of(), Set.of("p1"), Set.of("menu:project"));
        var result = (ProjectQueryResult)tool.execute(new ProjectOutputCompareInput("YOY", "MONTH", group(), null), context);
        assertThat(result.rows()).containsExactly(Map.of("difference", 1));
        assertThat(result.appliedFilters()).contains(Map.of("comparisonGroup", "current", "projectId", "p1"));
    }
    @Test void missingProjectMenuCannotCallComparisonDirectly() {
        var tool = new ProjectOutputCompareTool((c, i) -> { throw new AssertionError(); },
                new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        assertThatThrownBy(() -> tool.execute(new ProjectOutputCompareInput("YOY", "MONTH", group(), null),
                new ToolContext("t", "u", "i", Set.of("p1"))))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class).extracting("code").isEqualTo("AGENT_PROJECT_MENU_FORBIDDEN");
    }
    @Test void automaticBaselineCannotBeOverriddenAndDateDimensionMustMatch() {
        assertThatThrownBy(() -> new ProjectOutputCompareInput("YOY", "MONTH", group(), group()))
                .isInstanceOf(IllegalArgumentException.class);
        var baseline = new ProjectOutputQueryInput("p1", null, null, null, "projectCreateDate", "2025-09-01", "2025-09-30", null, null);
        assertThatThrownBy(() -> new ProjectOutputCompareInput("CUSTOM", null, group(), baseline))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
