package com.smart.agent.tool.operation;

public record ProjectOperationQueryInput(String projectId, String projectCode, String projectName,
                                         String organizationId, String projectNature, String managerId,
                                         String firstPartyId, String mode, String dateMode, String dateField,
                                         String startDate, String endDate, Integer page, Integer pageSize) {
    public ProjectOperationQueryInput {
        projectId = clean(projectId); projectCode = clean(projectCode); projectName = clean(projectName);
        organizationId = clean(organizationId); projectNature = clean(projectNature); managerId = clean(managerId); firstPartyId = clean(firstPartyId);
        mode = clean(mode) == null ? "detail" : mode;
        dateMode = clean(dateMode) == null ? "CURRENT" : dateMode;
        dateField = clean(dateField); startDate = clean(startDate); endDate = clean(endDate);
        page = page == null ? 1 : page; pageSize = pageSize == null ? 20 : pageSize;
    }
    public ProjectOperationQueryInput withProjectId(String id) {
        return new ProjectOperationQueryInput(id, null, null, organizationId, projectNature, managerId,
                firstPartyId, mode, dateMode, dateField, startDate, endDate, page, pageSize);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
