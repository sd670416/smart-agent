package com.smart.agent.tool.receipts;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectReceiptsCompareToolTest {
    private final ToolContext context = new ToolContext("t", "u", "i", Set.of(), Set.of("p1"),
            Set.of("menu:finance:payment-registration", "menu:finance:other-income-registration"));
    private ProjectReceiptsQueryInput group(String id) {
        return new ProjectReceiptsQueryInput(id, null, null, "receivedDate", "2026-09-01", "2026-09-30", null);
    }
    @Test void compareResolvesScopeAndReturnsBackendResultWithoutLocalCalculation() {
        var expected = new ProjectQueryResult("aggregate", 1, 6, 6L, false, List.of(),
                List.of(Map.of("metric", "已收项目款合计（元）", "difference", new java.math.BigDecimal("12.34"))), List.of());
        ProjectReceiptsBusinessClient client = new ProjectReceiptsBusinessClient() {
            public ProjectQueryResult query(ToolContext c, ProjectReceiptsQueryInput i) { throw new AssertionError(); }
            public ProjectQueryResult compare(ToolContext c, ProjectReceiptsCompareInput i) {
                assertThat(i.current().projectId()).isNull();
                assertThat(c.projectIds()).containsExactly("p1");
                assertThat(i.comparisonType()).isEqualTo("YOY"); return expected;
            }
        };
        var tool = new ProjectReceiptsCompareTool(client, new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        // An all-visible-project request does not require project-name resolution.
        assertThat(tool.execute(new ProjectReceiptsCompareInput("YOY", "MONTH", group(null), null), context)).isEqualTo(expected);
    }
    @Test void comparisonRequiresBothMenusBeforeAnyBusinessCall() {
        var tool = new ProjectReceiptsCompareTool((c, i) -> { throw new AssertionError(); },
                new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        var denied = new ToolContext("t", "u", "i", Set.of(), Set.of("p1"), Set.of("menu:finance:payment-registration"));
        assertThatThrownBy(() -> tool.execute(new ProjectReceiptsCompareInput("YOY", "MONTH", group(null), null), denied))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class).extracting("code").isEqualTo("AGENT_TOOL_FORBIDDEN");
    }
    @Test void automaticComparisonRejectsInventedBaselineAndAmbiguousPeriod() {
        assertThatThrownBy(() -> new ProjectReceiptsCompareInput("YOY", "MONTH", group(null), group(null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectReceiptsCompareInput("MOM", null, group(null), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
