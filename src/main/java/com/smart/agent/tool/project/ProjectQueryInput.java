package com.smart.agent.tool.project;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record ProjectQueryInput(List<String> select, ProjectQueryFilter filter, List<String> groupBy,
                                List<ProjectQueryAggregation> aggregations, List<ProjectQueryOrder> orderBy,
                                Integer page, Integer pageSize) {
    public ProjectQueryInput {
        select = normalizeFields(select);
        groupBy = normalizeFields(groupBy);
        Map<String, String> aliases = new HashMap<>();
        Set<String> usedAliases = new HashSet<>();
        List<ProjectQueryAggregation> normalizedAggregations = new ArrayList<>();
        if (aggregations != null) {
            for (int index = 0; index < aggregations.size(); index++) {
                ProjectQueryAggregation aggregation = aggregations.get(index);
                if (aggregation == null) {
                    normalizedAggregations.add(null);
                    continue;
                }
                String field = normalizeField(aggregation.field());
                String alias = normalizeAggregationAlias(aggregation.alias(), aggregation.function(), field, index, usedAliases);
                if (aggregation.alias() != null) aliases.put(aggregation.alias().trim(), alias);
                normalizedAggregations.add(new ProjectQueryAggregation(lower(aggregation.function()), field, alias));
            }
        }
        aggregations = java.util.Collections.unmodifiableList(normalizedAggregations);
        orderBy = orderBy == null ? List.of() : orderBy.stream()
                .map(o -> o == null ? null : new ProjectQueryOrder(
                        aliases.getOrDefault(o.field() == null ? "" : o.field().trim(), normalizeField(o.field())),
                        lower(o.direction())))
                .toList();
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be between 1 and 100");
    }

    private static List<String> normalizeFields(List<String> fields) {
        return fields == null ? List.of() : fields.stream().map(ProjectQueryInput::normalizeField).toList();
    }

    private static String normalizeField(String value) {
        if (value == null) return null;
        String field = value.trim();
        if (field.equals(chars(0x9879, 0x76ee, 0x72b6, 0x6001))
                || field.equals(chars(0x72b6, 0x6001))) return "projectStatus";
        if (field.equals(chars(0x9879, 0x76ee, 0x540d, 0x79f0))) return "projectName";
        if (field.equals(chars(0x9879, 0x76ee, 0x7f16, 0x53f7))) return "projectCode";
        return switch (field) {
            case "\u9879\u76ee\u540d\u79f0" -> "projectName";
            case "\u9879\u76ee\u7f16\u53f7" -> "projectCode";
            case "\u9879\u76ee\u72b6\u6001", "\u72b6\u6001" -> "projectStatus";
            case "\u9879\u76ee\u7c7b\u578b", "\u7c7b\u578b" -> "projectType";
            case "\u9879\u76ee\u9884\u7b97", "\u9884\u7b97" -> "projectBudget";
            case "\u6240\u5c5e\u7ec4\u7ec7" -> "organizationName";
            case "\u9879\u76ee\u6027\u8d28" -> "projectNature";
            case "\u8054\u8425\u5355\u4f4d", "\u8054\u8425\u516c\u53f8" -> "affiliatedCompanyName";
            case "\u5efa\u8bbe\u5355\u4f4d" -> "constructionCompany";
            case "\u9879\u76ee\u7ecf\u7406", "\u9879\u76ee\u8d1f\u8d23\u4eba" -> "personInChargeName";
            case "\u521b\u5efa\u65f6\u95f4", "\u521b\u5efa\u65e5\u671f" -> "createDate";
            case "\u66f4\u65b0\u65f6\u95f4", "\u66f4\u65b0\u65e5\u671f" -> "updateDate";
            default -> value.trim();
        };
    }

    private static String chars(int... codePoints) {
        StringBuilder result = new StringBuilder();
        for (int codePoint : codePoints) result.appendCodePoint(codePoint);
        return result.toString();
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeAggregationAlias(String alias, String function, String field, int index,
                                                     Set<String> usedAliases) {
        String candidate = alias == null ? "" : alias.trim();
        if (!candidate.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            String normalizedFunction = lower(function);
            if ("count".equals(normalizedFunction) && field == null) candidate = "projectCount";
            else if (field != null && normalizedFunction != null && !normalizedFunction.isBlank()) {
                candidate = field + Character.toUpperCase(normalizedFunction.charAt(0)) + normalizedFunction.substring(1);
            } else candidate = "aggregation" + (index + 1);
        }
        String unique = candidate;
        int suffix = 2;
        while (!usedAliases.add(unique)) unique = candidate + suffix++;
        return unique;
    }
}
