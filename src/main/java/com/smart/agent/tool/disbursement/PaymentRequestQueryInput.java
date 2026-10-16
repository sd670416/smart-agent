package com.smart.agent.tool.disbursement;

import java.time.LocalDate;
import java.util.Set;

public record PaymentRequestQueryInput(String projectId, String projectName, String registrationCode,
        String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
        String projectCode, String projectNameMatch, String requestId) {
    public PaymentRequestQueryInput(String projectId, String projectName, String registrationCode,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize) {
        this(projectId, projectName, registrationCode, mode, dateField, startDate, endDate, page, pageSize, null, null, null);
    }

    public PaymentRequestQueryInput {
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
        if (dateField != null && !Set.of("projectCreateDate", "createDate").contains(dateField))
            throw new IllegalArgumentException("Unsupported date field");
        if (startDate != null || endDate != null) {
            if (dateField == null || startDate == null || endDate == null)
                throw new IllegalArgumentException("Confirm the date field and both dates");
            if (LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate))) throw new IllegalArgumentException("Reversed date range");
            LocalDate.parse(endDate).plusDays(1);
        }
    }

    public PaymentRequestQueryInput withProjectId(String id) {
        return new PaymentRequestQueryInput(id, null, registrationCode, mode, dateField, startDate, endDate,
                page, pageSize, null, "exact", requestId);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
