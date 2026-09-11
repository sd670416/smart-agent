package com.smart.agent.tool.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ProjectOverviewResult(
        String projectId,
        String projectName,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String status,
        String statusName,
        Double progress,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String approvalStatus,
        String projectCode,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String projectType,
        String projectTypeName,
        BigDecimal projectBudget) {

    public ProjectOverviewResult(String projectId, String projectName, String status, double progress) {
        this(projectId, projectName, status, null, progress, null, null, null, null, null);
    }
}
