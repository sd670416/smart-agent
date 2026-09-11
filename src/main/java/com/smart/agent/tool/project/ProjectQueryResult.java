package com.smart.agent.tool.project;

import java.util.List;
import java.util.Map;

public record ProjectQueryResult(String mode, Integer page, Integer pageSize, Long total, Boolean hasNext,
                                 List<ProjectQueryColumn> columns, List<Map<String, Object>> rows,
                                 List<Map<String, Object>> appliedFilters) {
    public ProjectQueryResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
        appliedFilters = appliedFilters == null ? List.of() : List.copyOf(appliedFilters);
    }

    public static ProjectQueryResult empty(int page, int pageSize) {
        return new ProjectQueryResult("DETAIL", page, pageSize, 0L, false, List.of(), List.of(), List.of());
    }
}
