package com.smart.agent.tool.output;

public record ProjectOutputQueryInput(String projectId, String projectName, String outputReportNo,
        String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
        String projectCode, String projectNameMatch, String outputReportId) {
    public ProjectOutputQueryInput(String projectId, String projectName, String outputReportNo,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
            String projectCode, String projectNameMatch) {
        this(projectId, projectName, outputReportNo, mode, dateField, startDate, endDate, page, pageSize,
                projectCode, projectNameMatch, null);
    }
    public ProjectOutputQueryInput(String projectId, String projectName, String outputReportNo,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize) {
        this(projectId, projectName, outputReportNo, mode, dateField, startDate, endDate, page, pageSize, null, null);
    }

    public ProjectOutputQueryInput {
        projectId = clean(projectId);
        outputReportId = clean(outputReportId);
        projectCode = clean(projectCode);
        projectName = clean(projectName);
        projectNameMatch = projectNameMatch == null ? "exact" : projectNameMatch;
        if (!java.util.List.of("exact", "contains").contains(projectNameMatch))
            throw new IllegalArgumentException("Unsupported project name matching mode");
        if ("contains".equals(projectNameMatch) && (projectName == null || projectId != null || projectCode != null))
            throw new IllegalArgumentException("Contains matching cannot replace an exact project reference");
        mode = mode == null ? "detail" : mode;
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        if (!java.util.List.of("detail", "summary", "items").contains(mode)) throw new IllegalArgumentException("Unsupported mode");
        if ("items".equals(mode) && outputReportId == null) throw new IllegalArgumentException("Select an output report first");
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        if (dateField != null && !java.util.List.of("projectCreateDate", "createDate").contains(dateField))
            throw new IllegalArgumentException("Unsupported date field");
        if (startDate != null || endDate != null) {
            if (dateField == null || startDate == null || endDate == null)
                throw new IllegalArgumentException("Confirm the date field and provide both dates");
            if (java.time.LocalDate.parse(startDate).isAfter(java.time.LocalDate.parse(endDate)))
                throw new IllegalArgumentException("Reversed date range");
        }
    }

    public ProjectOutputQueryInput withProjectId(String id) {
        return new ProjectOutputQueryInput(id, null, outputReportNo, mode, dateField, startDate, endDate,
                page, pageSize, null, "exact", outputReportId);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

