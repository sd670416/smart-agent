package com.smart.agent.tool.material;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MaterialRefundCompareToolTest {
    private final ToolContext context = new ToolContext("t","u","i",Set.of(),Set.of("p1"),Set.of("menu:material:refund"));
    private MaterialRefundQueryInput group(String name) {
        return new MaterialRefundQueryInput(null,name,null,"summary","returnDate","2026-09-01","2026-09-30",1,20);
    }
    @Test void delegatesRealtimeComparisonAndPreservesBackendAmounts() {
        var expected=new ProjectQueryResult("aggregate",1,3,3L,false,List.of(),List.of(Map.of("difference",new java.math.BigDecimal("12.34"))),List.of());
        MaterialRefundBusinessClient client=new MaterialRefundBusinessClient() {
            public ProjectQueryResult query(ToolContext c,MaterialRefundQueryInput i) { throw new AssertionError(); }
            public ProjectQueryResult compare(ToolContext c,MaterialRefundCompareInput i) {
                assertThat(c.projectIds()).containsExactly("p1"); assertThat(i.comparisonType()).isEqualTo("YOY"); return expected;
            }
        };
        var tool=new MaterialRefundCompareTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        assertThat(tool.execute(new MaterialRefundCompareInput("YOY","MONTH",group(null),null),context)).isEqualTo(expected);
    }
    @Test void deniesBeforeAnyBusinessCall() {
        var tool=new MaterialRefundCompareTool((c,i)->{throw new AssertionError();},new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        assertThatThrownBy(()->tool.execute(new MaterialRefundCompareInput("YOY","MONTH",group(null),null),
                new ToolContext("t","u","i",Set.of("p1"))))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class).extracting("code").isEqualTo("AGENT_TOOL_FORBIDDEN");
    }
    @Test void invalidComparisonDoesNotInventDatesOrChangeDimensions() {
        var empty=new MaterialRefundQueryInput(null,null,null,"summary",null,null,null,1,20);
        assertThatThrownBy(()->new MaterialRefundCompareInput("CUSTOM",null,empty,empty)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MaterialRefundCompareInput("YOY","MONTH",group(null),group(null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MaterialRefundCompareInput("MOM",null,group(null),null)).isInstanceOf(IllegalArgumentException.class);
        var baseline=new MaterialRefundQueryInput(null,null,null,"summary","projectCreateDate","2026-01-01","2026-01-31",1,20);
        assertThatThrownBy(()->new MaterialRefundCompareInput("CUSTOM",null,group(null),baseline)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void comparisonPeriodClarificationDoesNotFetchData() {
        var prompt=new MaterialRefundClarifyDateTool().execute(new MaterialRefundClarifyDateTool.Input("PERIOD"),context);
        assertThat(prompt.ambiguityType()).isEqualTo("COMPARISON_PERIOD");
        assertThat(prompt.options()).extracting(ClarificationPrompt.Option::id).containsExactly("period:MONTH","period:QUARTER","period:YEAR");
    }
}
