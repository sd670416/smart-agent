package com.smart.agent.tool.receipts;

import java.time.LocalDate;
import java.util.List;

public record ProjectReceiptsQueryInput(String projectId, String projectName, String projectCode,
        String dateField, String startDate, String endDate, String projectNameMatch) {
    public ProjectReceiptsQueryInput {
        projectId = clean(projectId);
        projectName = clean(projectName);
        projectCode = clean(projectCode);
        projectNameMatch = projectNameMatch == null ? "exact" : projectNameMatch;
        if (!List.of("exact", "contains").contains(projectNameMatch)
                || "contains".equals(projectNameMatch) && (projectName == null || projectId != null || projectCode != null))
            throw new IllegalArgumentException("Invalid project matching mode");
        if (dateField != null && !List.of("receivedDate", "createDate", "projectCreateDate").contains(dateField))
            throw new IllegalArgumentException("Unsupported date field");
        if (startDate != null || endDate != null) {
            if (dateField == null || startDate == null || endDate == null)
                throw new IllegalArgumentException("Confirm the date field and both dates");
            if (LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate)))
                throw new IllegalArgumentException("Reversed date range");
            LocalDate.parse(endDate).plusDays(1);
        }
    }
    public ProjectReceiptsQueryInput withProjectId(String id) {
        return new ProjectReceiptsQueryInput(id, null, null, dateField, startDate, endDate, "exact");
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
