package com.smart.agent.tool.project;

public record ProjectOverviewInput(String projectId) {
    public ProjectOverviewInput {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
    }
}
