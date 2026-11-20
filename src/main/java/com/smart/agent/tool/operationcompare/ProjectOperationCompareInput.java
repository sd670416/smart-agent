package com.smart.agent.tool.operationcompare;

import java.util.List;

public record ProjectOperationCompareInput(List<ProjectSelection> projects, List<String> metricKeys,
        String baselineProjectId, String dateMode, String dateField, String startDate, String endDate) {
    public record ProjectSelection(String projectId, String projectCode, String projectName) { }
    public ProjectOperationCompareInput {
        projects = projects == null ? List.of() : List.copyOf(projects);
        metricKeys = metricKeys == null ? null : List.copyOf(metricKeys);
        dateMode = dateMode == null || dateMode.isBlank() ? "CURRENT" : dateMode.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
