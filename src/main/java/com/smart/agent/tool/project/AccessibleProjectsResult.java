package com.smart.agent.tool.project;

import java.util.List;

public record AccessibleProjectsResult(int page, int pageSize, long total, boolean hasNext,
                                       List<AccessibleProjectItem> items) {}
