package com.smart.agent.tool.project;

public record ProjectContractsInput(String projectId, String contractType, Integer page, Integer pageSize) {
    public ProjectContractsInput {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank");
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        if (pageSize < 1 || pageSize > 20) throw new IllegalArgumentException("pageSize must be between 1 and 20");
    }
}
