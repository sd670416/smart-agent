package com.smart.agent.tool.project;

import java.util.List;

public record ProjectQueryFilter(String logic, List<ProjectQueryCondition> conditions,
                                 List<ProjectQueryFilter> groups) {
    public ProjectQueryFilter {
        logic = logic == null ? "AND" : logic.trim().toUpperCase(java.util.Locale.ROOT);
        conditions = conditions == null ? List.of() : conditions.stream().map(c -> c == null ? null
                : new ProjectQueryCondition(c.field(), normalizeOperator(c.operator()), c.value())).toList();
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    private static String normalizeOperator(String operator) {
        if (operator == null) return null;
        String value = operator.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case ">=" -> "gte";
            case "<=" -> "lte";
            case ">" -> "gt";
            case "<" -> "lt";
            case "=" -> "eq";
            default -> value;
        };
    }
}
