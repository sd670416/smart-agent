package com.smart.agent.tool.partnerhistory;

public record PartnerHistoryQueryInput(String partnerType, String partnerId, String partnerName,
        String contractType, String contractId, String recordId,
        String projectId, String projectCode, String projectName, String projectNature,
        String managerId, String organizationId, String mode, String dateField,
        String startDate, String endDate, Integer page, Integer pageSize) {
    public PartnerHistoryQueryInput {
        partnerType = clean(partnerType);
        if (partnerType != null) partnerType = partnerType.toUpperCase(java.util.Locale.ROOT);
        mode = clean(mode); if (mode == null) mode = "detail";
        partnerId = clean(partnerId); partnerName = clean(partnerName);
        projectId = clean(projectId); projectCode = clean(projectCode); projectName = clean(projectName);
        contractType = clean(contractType); contractId = clean(contractId); recordId = clean(recordId);
    }
    public PartnerHistoryQueryInput withProjectId(String id) {
        return new PartnerHistoryQueryInput(partnerType, partnerId, partnerName, contractType, contractId, recordId,
                id, null, null, projectNature, managerId, organizationId, mode, dateField, startDate, endDate, page, pageSize);
    }
    public PartnerHistoryQueryInput withPartner(String id) {
        return new PartnerHistoryQueryInput(partnerType, id, null, contractType, contractId, recordId,
                projectId, projectCode, projectName, projectNature, managerId, organizationId, mode, dateField, startDate, endDate, page, pageSize);
    }
    public PartnerHistoryQueryInput lookup() {
        return new PartnerHistoryQueryInput(partnerType, partnerId, partnerName, contractType, contractId, null,
                projectId, projectCode, projectName, projectNature, managerId, organizationId, "partners", dateField, startDate, endDate, 1, 100);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
