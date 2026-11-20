package com.smart.agent.tool.profit.ranking;

public record ProjectProfitRankingInput(
        String projectId, String projectCode, String projectName,
        String organizationId, String projectNature, String projectType, String managerId,
        String metric, String direction, Integer topN, Integer page, Integer pageSize,
        Boolean lossOnly, String dateMode, String dateField, String startDate, String endDate) {
    public ProjectProfitRankingInput {
        projectId = trim(projectId); projectCode = trim(projectCode); projectName = trim(projectName);
        organizationId = trim(organizationId); projectNature = trim(projectNature);
        projectType = trim(projectType); managerId = trim(managerId);
        metric = fallback(metric, "budgetProfitRate"); direction = fallback(direction, "desc");
        topN = topN == null ? 20 : topN; page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize; dateMode = fallback(dateMode, "CURRENT");
    }
    public ProjectProfitRankingInput withProjectId(String id) {
        return new ProjectProfitRankingInput(id, null, null, organizationId, projectNature, projectType, managerId,
                metric, direction, topN, page, pageSize, lossOnly, dateMode, dateField, startDate, endDate);
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String fallback(String value, String fallback) { String text = trim(value); return text == null ? fallback : text; }
}
