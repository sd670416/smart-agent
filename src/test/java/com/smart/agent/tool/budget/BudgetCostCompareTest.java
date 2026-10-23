package com.smart.agent.tool.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.BudgetCostDomainContributor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BudgetCostCompareTest {
    private Object input(String json) throws Exception {
        Class<?> type;
        try {type=Class.forName("com.smart.agent.tool.budget.BudgetCostCompareInput");}
        catch(ClassNotFoundException e){throw new AssertionError("Current budget comparison input missing",e);}
        return new ObjectMapper().readValue(json,type);
    }
    @SuppressWarnings("unchecked") private AgentTool<Object,Object> tool() throws Exception {
        try {return (AgentTool<Object,Object>)Class.forName("com.smart.agent.tool.budget.BudgetCostCompareTool")
                .getConstructor(BudgetCostBusinessClient.class,ProjectReferenceResolver.class)
                .newInstance((BudgetCostBusinessClient)(context,input)->{throw new AssertionError("No query permitted");},
                        new ProjectReferenceResolver(new LocalProjectBusinessClient()));}
        catch(ClassNotFoundException e){throw new AssertionError("Budget comparison tool missing",e);}
    }
    @Test void acceptsOnlyTwoExplicitCurrentSummaryGroupsWithSameDateDimension() throws Exception {
        assertThat(input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}")).isNotNull();
        for(String json:List.of("{\"comparisonType\":\"YOY\",\"current\":{},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"mode\":\"detail\"},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"dateField\":\"projectCreateDate\"},\"baseline\":{}}"))
            assertThatThrownBy(()->input(json)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void budgetMenuIsRequiredBeforeResolvingProjectsOrReadingBusinessData() throws Exception {
        var tool=tool();var input=input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}");
        assertThatThrownBy(()->tool.execute(input,new ToolContext("t","u","i",Set.of(),Set.of("p"),Set.of("menu:project"))))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class);
    }
    @Test void comparisonBelongsToExistingBudgetDomain() {
        assertThat(new BudgetCostDomainContributor().descriptor().toolKeys()).contains("budgetCost.compare");
    }
}
