package com.smart.agent.tool.cost;

import java.util.Objects;

public record CostContractCompareInput(String comparisonType,CostContractQueryInput current,CostContractQueryInput baseline) {
    public CostContractCompareInput {
        if(!"CUSTOM".equals(comparisonType) || current==null || baseline==null)
            throw new IllegalArgumentException("Two custom current-structure groups required");
        for(var group:java.util.List.of(current,baseline))
            if(!"all".equals(group.source()) || !"summary".equals(group.mode()) || group.contractId()!=null)
                throw new IllegalArgumentException("Compare all five current sources, not individual contracts");
        if(!Objects.equals(current.dateField(),baseline.dateField()))
            throw new IllegalArgumentException("Both groups require the same project creation date dimension");
    }
}
