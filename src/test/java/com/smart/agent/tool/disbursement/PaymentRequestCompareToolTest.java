package com.smart.agent.tool.disbursement;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentRequestCompareToolTest {
    private final ToolContext context = new ToolContext("t","u","i",Set.of(),Set.of("p1"),Set.of("menu:finance:payment-request"));
    private PaymentRequestQueryInput group(String name) {
        return new PaymentRequestQueryInput(null,name,null,"summary","createDate","2026-09-01","2026-09-30",1,20);
    }
    @Test void delegatesRealtimeComparisonAndPreservesBackendAmounts() {
        var expected=new ProjectQueryResult("aggregate",1,3,3L,false,List.of(),List.of(Map.of("difference",new java.math.BigDecimal("12.34"))),List.of());
        PaymentRequestBusinessClient client=new PaymentRequestBusinessClient() {
            public ProjectQueryResult query(ToolContext c,PaymentRequestQueryInput i) { throw new AssertionError(); }
            public ProjectQueryResult compare(ToolContext c,PaymentRequestCompareInput i) {
                assertThat(c.projectIds()).containsExactly("p1"); assertThat(i.comparisonType()).isEqualTo("YOY"); return expected;
            }
        };
        var tool=new PaymentRequestCompareTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        assertThat(tool.execute(new PaymentRequestCompareInput("YOY","MONTH",group(null),null),context)).isEqualTo(expected);
    }
    @Test void deniesBeforeAnyBusinessCall() {
        var tool=new PaymentRequestCompareTool((c,i)->{throw new AssertionError();},new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        assertThatThrownBy(()->tool.execute(new PaymentRequestCompareInput("YOY","MONTH",group(null),null),
                new ToolContext("t","u","i",Set.of("p1"))))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class).extracting("code").isEqualTo("AGENT_TOOL_FORBIDDEN");
    }
    @Test void invalidComparisonDoesNotInventDatesOrChangeDimensions() {
        var empty=new PaymentRequestQueryInput(null,null,null,"summary",null,null,null,1,20);
        assertThatThrownBy(()->new PaymentRequestCompareInput("CUSTOM",null,empty,empty)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PaymentRequestCompareInput("YOY","MONTH",group(null),group(null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PaymentRequestCompareInput("MOM",null,group(null),null)).isInstanceOf(IllegalArgumentException.class);
        var baseline=new PaymentRequestQueryInput(null,null,null,"summary","projectCreateDate","2026-01-01","2026-01-31",1,20);
        assertThatThrownBy(()->new PaymentRequestCompareInput("CUSTOM",null,group(null),baseline)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void comparisonPeriodClarificationDoesNotFetchData() {
        var prompt=new PaymentRequestClarifyDateTool().execute(new PaymentRequestClarifyDateTool.Input("PERIOD"),context);
        assertThat(prompt.ambiguityType()).isEqualTo("COMPARISON_PERIOD");
        assertThat(prompt.options()).extracting(ClarificationPrompt.Option::id).containsExactly("period:MONTH","period:QUARTER","period:YEAR");
    }
}
