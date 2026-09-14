package com.smart.agent.tool.project;

public record ProjectArchiveDetailInput(String projectId, String projectCode, String projectName) {
    public ProjectArchiveDetailInput {
        if (isBlank(projectId) && isBlank(projectCode) && isBlank(projectName)) {
            throw new IllegalArgumentException("project identifier must not be blank");
        }
    }

    String identifier() {
        if (!isBlank(projectId)) return projectId.trim();
        if (!isBlank(projectCode)) return projectCode.trim();
        return projectName.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
