package com.smart.agent.tool.contract;

public record ConstructionContractQueryInput(String projectId, String projectName, String contractName,
        String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
        String projectCode, String projectNameMatch, String groupBy, String projectType, String projectTypeName) {
    public ConstructionContractQueryInput(String projectId, String projectName, String contractName,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize,
            String projectCode, String projectNameMatch) {
        this(projectId, projectName, contractName, mode, dateField, startDate, endDate, page, pageSize,
                projectCode, projectNameMatch, null, null, null);
    }
    public ConstructionContractQueryInput(String projectId, String projectName, String contractName,
            String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize) {
        this(projectId, projectName, contractName, mode, dateField, startDate, endDate, page, pageSize, null, null);
    }

    public ConstructionContractQueryInput {
        projectId = clean(projectId);
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
        if (!java.util.List.of("detail", "summary").contains(mode)) throw new IllegalArgumentException("Unsupported mode");
        groupBy = clean(groupBy);
        projectType = clean(projectType);
        projectTypeName = clean(projectTypeName);
        if (groupBy != null && (!"projectType".equals(groupBy) || !"summary".equals(mode)))
            throw new IllegalArgumentException("Project type grouping requires summary mode");
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        if (dateField != null && !java.util.List.of("projectCreateDate", "signingDate", "createDate").contains(dateField))
            throw new IllegalArgumentException("Unsupported date field");
        if (startDate != null || endDate != null) {
            if (dateField == null || startDate == null || endDate == null)
                throw new IllegalArgumentException("Confirm the date field and provide both dates");
            if (java.time.LocalDate.parse(startDate).isAfter(java.time.LocalDate.parse(endDate)))
                throw new IllegalArgumentException("Reversed date range");
        }
    }

    public ConstructionContractQueryInput withProjectId(String id) {
        return new ConstructionContractQueryInput(id, null, contractName, mode, dateField, startDate, endDate,
                page, pageSize, null, "exact", groupBy, projectType, projectTypeName);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
