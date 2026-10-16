package com.smart.agent.tool.receipts;

import java.util.List;

public record ProjectReceiptsCompareInput(String comparisonType, String periodUnit,
        ProjectReceiptsQueryInput current, ProjectReceiptsQueryInput baseline) {
    public ProjectReceiptsCompareInput {
        if (!List.of("YOY", "MOM", "CUSTOM").contains(comparisonType == null ? "" : comparisonType) || current == null)
            throw new IllegalArgumentException("Comparison type and current group required");
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
