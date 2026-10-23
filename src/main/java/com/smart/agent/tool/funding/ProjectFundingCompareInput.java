package com.smart.agent.tool.funding;

import java.util.Objects;

public record ProjectFundingCompareInput(String comparisonType,ProjectFundingQueryInput current,ProjectFundingQueryInput baseline) {
    public ProjectFundingCompareInput {
        if(!"CUSTOM".equals(comparisonType) || current==null || baseline==null)
            throw new IllegalArgumentException("Two custom current cumulative groups required");
        if(!"summary".equals(current.mode()) || !"summary".equals(baseline.mode())
                || !Objects.equals(current.metric(),baseline.metric()) || !Objects.equals(current.dateField(),baseline.dateField()))
            throw new IllegalArgumentException("Same metric, date dimension and full summaries required");
    }
}
