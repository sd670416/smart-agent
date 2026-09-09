package com.smart.agent.tool.project;

public record AccessibleProjectsInput(String keyword, String status, Integer page, Integer pageSize) {
    public AccessibleProjectsInput {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        if (pageSize < 1 || pageSize > 20) throw new IllegalArgumentException("pageSize must be between 1 and 20");
    }
}
