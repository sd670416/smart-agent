package com.smart.agent.tool.contract;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConstructionContractCompareToolTest {
    @Test void groupedComparisonRetainsGroupingThroughProjectResolution() throws Exception {
        var grouped = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                "{\"mode\":\"summary\",\"groupBy\":\"projectType\",\"dateField\":\"signingDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}",ConstructionContractQueryInput.class);
        var captured = new java.util.concurrent.atomic.AtomicReference<ConstructionContractCompareInput>();
        ConstructionContractBusinessClient client = new ConstructionContractBusinessClient() {
            public ProjectQueryResult query(ToolContext c, ConstructionContractQueryInput i) { throw new AssertionError(); }
            public ProjectQueryResult compare(ToolContext c, ConstructionContractCompareInput i) {
                captured.set(i);
                return new ProjectQueryResult("aggregate",1,0,0L,false,List.of(),List.of(),List.of());
            }
        };
        var tool = new ConstructionContractCompareTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        tool.execute(new ConstructionContractCompareInput("YOY","MONTH",grouped.withProjectId("p1"),null),
                new ToolContext("t","u","i",Set.of(),Set.of("p1"),Set.of("menu:project")));
        assertThat(captured.get().current().groupBy()).isEqualTo("projectType");
        assertThat(captured.get().current().projectId()).isEqualTo("p1");
        assertThat(captured.get().current().page()).isEqualTo(1);
    }
    @Test void customComparisonRequiresSameGroupingOnBothSides() throws Exception {
        var grouped = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                "{\"mode\":\"summary\",\"groupBy\":\"projectType\",\"dateField\":\"signingDate\"}",ConstructionContractQueryInput.class);
        assertThatThrownBy(() -> new ConstructionContractCompareInput("CUSTOM",null,grouped,group()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private ConstructionContractQueryInput group() {
        return new ConstructionContractQueryInput("p1", null, "主体", "detail", "signingDate", "2026-09-01", "2026-09-30", 9, 20);
    }
    @Test void resolvesEachGroupAndForcesSummaryInsteadOfComparingPage() {
        ConstructionContractBusinessClient client = new ConstructionContractBusinessClient() {
            public ProjectQueryResult query(ToolContext c, ConstructionContractQueryInput i) { throw new AssertionError(); }
            public ProjectQueryResult compare(ToolContext c, ConstructionContractCompareInput i) {
                assertThat(i.current().mode()).isEqualTo("summary"); assertThat(i.current().page()).isEqualTo(1);
                assertThat(i.current().projectId()).isEqualTo("p1"); assertThat(i.current().contractName()).isEqualTo("主体");
                return new ProjectQueryResult("aggregate", 1, 2, 2L, false, List.of(), List.of(Map.of("difference", 1)), List.of());
            }
        };
        var tool = new ConstructionContractCompareTool(client, new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        var context = new ToolContext("t", "u", "i", Set.of(), Set.of("p1"), Set.of("menu:project"));
        var result = (ProjectQueryResult)tool.execute(new ConstructionContractCompareInput("YOY", "MONTH", group(), null), context);
        assertThat(result.rows()).containsExactly(Map.of("difference", 1));
        assertThat(result.appliedFilters()).contains(Map.of("comparisonGroup", "current", "projectId", "p1"));
    }
    @Test void missingProjectMenuCannotCallComparisonDirectly() {
        var tool = new ConstructionContractCompareTool((c, i) -> { throw new AssertionError(); },
                new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        assertThatThrownBy(() -> tool.execute(new ConstructionContractCompareInput("YOY", "MONTH", group(), null),
                new ToolContext("t", "u", "i", Set.of("p1"))))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class).extracting("code").isEqualTo("AGENT_PROJECT_MENU_FORBIDDEN");
    }
    @Test void automaticBaselineCannotBeOverriddenAndDateDimensionMustMatch() {
        assertThatThrownBy(() -> new ConstructionContractCompareInput("YOY", "MONTH", group(), group()))
                .isInstanceOf(IllegalArgumentException.class);
        var baseline = new ConstructionContractQueryInput("p1", null, null, null, "createDate", "2025-09-01", "2025-09-30", null, null);
        assertThatThrownBy(() -> new ConstructionContractCompareInput("CUSTOM", null, group(), baseline))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
