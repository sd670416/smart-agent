package com.smart.agent.tool.output;

import java.util.List;

public record ProjectOutputCompareInput(String comparisonType, String periodUnit,
        ProjectOutputQueryInput current, ProjectOutputQueryInput baseline) {
    public ProjectOutputCompareInput {
        if (current != null && (current.outputReportId() != null || "items".equals(current.mode()))
                || baseline != null && (baseline.outputReportId() != null || "items".equals(baseline.mode())))
            throw new IllegalArgumentException("Report drilldown cannot be used as a comparison group");
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
