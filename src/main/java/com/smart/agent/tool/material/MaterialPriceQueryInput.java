package com.smart.agent.tool.material;

import java.time.LocalDate;
import java.util.Set;

public record MaterialPriceQueryInput(String materialName, String specification, String unit,
        String projectId, String projectCode, String projectName, String startDate, String endDate,
        String sortBy, String sortDirection, Integer page, Integer pageSize) {
    public MaterialPriceQueryInput {
        materialName = clean(materialName); specification = clean(specification); unit = clean(unit);
        projectId = clean(projectId); projectCode = clean(projectCode); projectName = clean(projectName);
        startDate = clean(startDate); endDate = clean(endDate);
        if (materialName == null) throw new IllegalArgumentException("请先确认要查询的材料名称");
        sortBy = sortBy == null ? "signDate" : sortBy;
        sortDirection = sortDirection == null ? "asc" : sortDirection;
        if (!Set.of("signDate", "unitPrice").contains(sortBy) || !Set.of("asc", "desc").contains(sortDirection))
            throw new IllegalArgumentException("Unsupported sorting");
        page = page == null ? 1 : page; pageSize = pageSize == null ? 20 : pageSize;
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        if (startDate != null || endDate != null) {
            if (startDate == null || endDate == null || LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate)))
                throw new IllegalArgumentException("Invalid dates");
            LocalDate.parse(endDate).plusDays(1);
        }
    }
    public MaterialPriceQueryInput withProjectId(String id) {
        return new MaterialPriceQueryInput(materialName, specification, unit, id, null, null,
                startDate, endDate, sortBy, sortDirection, page, pageSize);
    }
    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
