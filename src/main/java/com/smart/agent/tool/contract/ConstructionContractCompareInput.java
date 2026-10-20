package com.smart.agent.tool.contract;

import java.util.List;

public record ConstructionContractCompareInput(String comparisonType, String periodUnit,
        ConstructionContractQueryInput current, ConstructionContractQueryInput baseline) {
    public ConstructionContractCompareInput {
        if (!List.of("YOY", "MOM", "CUSTOM").contains(comparisonType == null ? "" : comparisonType) || current == null)
            throw new IllegalArgumentException("Comparison type and current group required");
        if (current.groupBy() != null || baseline != null && baseline.groupBy() != null)
            throw new IllegalArgumentException("Grouped comparisons are not supported");
        if ("CUSTOM".equals(comparisonType)) {
            if (baseline == null || !java.util.Objects.equals(current.dateField(), baseline.dateField()))
                throw new IllegalArgumentException("Both groups must use the same date dimension");
        } else if (baseline != null || current.startDate() == null || current.endDate() == null
                || !List.of("MONTH", "QUARTER", "YEAR", "RANGE").contains(periodUnit == null ? "" : periodUnit)
                || "MOM".equals(comparisonType) && "RANGE".equals(periodUnit)) {
            throw new IllegalArgumentException("Explicit period and dates required; automatic baseline cannot be overridden");
        }
    }
}
