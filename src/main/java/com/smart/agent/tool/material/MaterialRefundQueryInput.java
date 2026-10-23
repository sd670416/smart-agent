package com.smart.agent.tool.material;

import java.time.LocalDate;
import java.util.Set;

public record MaterialRefundQueryInput(String projectId, String projectName, String registrationCode,
        String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
        String projectCode, String projectNameMatch, String requestId) {
    public MaterialRefundQueryInput(String projectId, String projectName, String registrationCode,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize) {
        this(projectId, projectName, registrationCode, mode, dateField, startDate, endDate, page, pageSize, null, null, null);
    }

    public MaterialRefundQueryInput {
        projectId = clean(projectId); projectCode = clean(projectCode); projectName = clean(projectName);
        registrationCode = clean(registrationCode); requestId = clean(requestId);
        projectNameMatch = projectNameMatch == null ? "exact" : projectNameMatch;
        if (!Set.of("exact", "contains").contains(projectNameMatch)
                || "contains".equals(projectNameMatch) && (projectName == null || projectId != null || projectCode != null))
            throw new IllegalArgumentException("Invalid project matching mode");
        mode = mode == null ? "detail" : mode;
        page = page == null ? 1 : page; pageSize = pageSize == null ? 20 : pageSize;
        if (!Set.of("detail", "items", "summary").contains(mode)) throw new IllegalArgumentException("Unsupported mode");
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        if (requestId != null && "summary".equals(mode))
            throw new IllegalArgumentException("Summary cannot inherit a single request ID");
        if (requestId != null && "detail".equals(mode)) page = 1;
        if (dateField != null && !Set.of("projectCreateDate", "createDate", "returnDate").contains(dateField))
            throw new IllegalArgumentException("Unsupported date field");
        if (startDate != null || endDate != null) {
            if (dateField == null || startDate == null || endDate == null)
                throw new IllegalArgumentException("Confirm the date field and both dates");
            if (LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate))) throw new IllegalArgumentException("Reversed date range");
            LocalDate.parse(endDate).plusDays(1);
        }
    }

    public MaterialRefundQueryInput withProjectId(String id) {
        return new MaterialRefundQueryInput(id, null, registrationCode, mode, dateField, startDate, endDate,
                page, pageSize, null, "exact", requestId);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
