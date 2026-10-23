package com.smart.agent.tool.budget;

import java.util.Objects;

public record BudgetCostCompareInput(String comparisonType,BudgetCostQueryInput current,BudgetCostQueryInput baseline) {
    public BudgetCostCompareInput {
        if(!"CUSTOM".equals(comparisonType) || current==null || baseline==null)
            throw new IllegalArgumentException("Two custom current-budget groups required");
        if(!"summary".equals(current.mode()) || !"summary".equals(baseline.mode()))
            throw new IllegalArgumentException("Compare full summaries, not project pages or individual cost items");
        if(!Objects.equals(current.dateField(),baseline.dateField()))
            throw new IllegalArgumentException("Both groups require the same project creation date dimension");
    }
}
