package com.smart.agent.tool.disbursement;

import java.util.List;
import java.util.Objects;

public record OtherPaymentRequestCompareInput(String comparisonType, String periodUnit,
        OtherPaymentRequestQueryInput current, OtherPaymentRequestQueryInput baseline) {
    public OtherPaymentRequestCompareInput {
        if (!List.of("YOY", "MOM", "CUSTOM").contains(comparisonType == null ? "" : comparisonType) || current == null)
            throw new IllegalArgumentException("Comparison type and current group required");
        if ("CUSTOM".equals(comparisonType)) {
            if (baseline == null || !Objects.equals(current.dateField(), baseline.dateField()))
                throw new IllegalArgumentException("Both groups must use the same date dimension");
        } else if (baseline != null || current.startDate() == null || current.endDate() == null
                || !List.of("MONTH", "QUARTER", "YEAR", "RANGE").contains(periodUnit == null ? "" : periodUnit)
                || "MOM".equals(comparisonType) && "RANGE".equals(periodUnit)) {
            throw new IllegalArgumentException("Explicit period and dates required; automatic baseline cannot be overridden");
        }
        validateGroup(current);
        if (baseline != null) validateGroup(baseline);
    }

    private static void validateGroup(OtherPaymentRequestQueryInput group) {
        if (group.dateField() == null || group.startDate() == null || group.endDate() == null)
            throw new IllegalArgumentException("Explicit date dimension and both dates required for each group");
        if (group.registrationCode() != null || group.requestId() != null)
            throw new IllegalArgumentException("Comparison supports project and date filters only");
    }
}
