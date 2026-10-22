package com.smart.agent.tool.income;

public record OtherIncomeRegistrationQueryInput(String projectId, String projectName, String registrationCode,
        String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
        String projectCode, String projectNameMatch, String registrationId) {
    public OtherIncomeRegistrationQueryInput(String projectId, String projectName, String registrationCode,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
            String projectCode, String projectNameMatch) {
        this(projectId, projectName, registrationCode, mode, dateField, startDate, endDate, page, pageSize,
                projectCode, projectNameMatch, null);
    }
    public OtherIncomeRegistrationQueryInput(String projectId, String projectName, String registrationCode,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize) {
        this(projectId, projectName, registrationCode, mode, dateField, startDate, endDate, page, pageSize, null, null);
    }

    public OtherIncomeRegistrationQueryInput {
        projectId = clean(projectId);
        projectCode = clean(projectCode);
        projectName = clean(projectName);
        projectNameMatch = projectNameMatch == null ? "exact" : projectNameMatch;
        if (!java.util.List.of("exact", "contains").contains(projectNameMatch))
            throw new IllegalArgumentException("Unsupported project name matching mode");
        if ("contains".equals(projectNameMatch) && (projectName == null || projectId != null || projectCode != null))
            throw new IllegalArgumentException("Contains matching cannot replace an exact project reference");
        mode = mode == null ? "detail" : mode;
        registrationId = clean(registrationId);
        if (registrationId != null && !java.util.List.of("detail", "items").contains(mode))
            throw new IllegalArgumentException("A registration ID is supported only in detail or items mode");
        if ("items".equals(mode) && registrationId == null)
            throw new IllegalArgumentException("Income items require a real parent registration ID");
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        if (!java.util.List.of("detail", "summary", "items").contains(mode)) throw new IllegalArgumentException("Unsupported mode");
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        if (registrationId != null && "detail".equals(mode)) page = 1;
        if (dateField != null && !java.util.List.of("projectCreateDate", "createDate", "receivedDate").contains(dateField))
            throw new IllegalArgumentException("Unsupported date field");
        if (startDate != null || endDate != null) {
            if (dateField == null || startDate == null || endDate == null)
                throw new IllegalArgumentException("Confirm the date field and provide both dates");
            if (java.time.LocalDate.parse(startDate).isAfter(java.time.LocalDate.parse(endDate)))
                throw new IllegalArgumentException("Reversed date range");
        }
    }

    public OtherIncomeRegistrationQueryInput withProjectId(String id) {
        return new OtherIncomeRegistrationQueryInput(id, null, registrationCode, mode, dateField, startDate, endDate,
                page, pageSize, null, "exact", registrationId);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
