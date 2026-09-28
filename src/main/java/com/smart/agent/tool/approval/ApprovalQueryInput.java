package com.smart.agent.tool.approval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record ApprovalQueryInput(String scope, String visibility, String personKeyword,
                                 List<String> select, Map<String, Object> filter,
                                 List<String> groupBy, List<Map<String, Object>> aggregations,
                                 List<Map<String, Object>> orderBy, Integer page,
                                 Integer pageSize, String recordMode, String processType) {
    private static final Pattern AGGREGATION_ALIAS =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    public ApprovalQueryInput {
        String normalizedScope = normalize(scope, null);
        if (isVisibilityValue(normalizedScope)) {
            if (visibility == null || visibility.isBlank()) visibility = normalizedScope;
            scope = "TODO";
        } else {
            scope = normalizeScope(scope);
        }
        visibility = normalizeVisibility(visibility);
        recordMode = normalizeRecordMode(recordMode);
        aggregations = normalizeAggregations(aggregations);
    }
    public int pageValue() { return page == null || page < 1 ? 1 : page; }
    public int pageSizeValue() { return pageSize == null || pageSize < 1 ? 20 : Math.min(100, pageSize); }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeScope(String value) {
        String normalized = normalize(value, "TODO");
        return switch (normalized) {
            case "待办" -> "TODO";
            case "已办" -> "PROCESSED";
            case "我发起" -> "STARTED";
            default -> normalized;
        };
    }

    private static String normalizeVisibility(String value) {
        String normalized = normalize(value, "SELF");
        return switch (normalized) {
            case "本人", "我的" -> "SELF";
            case "全部", "所有人" -> "ALL";
            default -> normalized;
        };
    }

    private static boolean isVisibilityValue(String value) {
        if (value == null) return false;
        String normalized = normalizeVisibility(value);
        return "SELF".equals(normalized) || "ALL".equals(normalized);
    }

    private static List<Map<String, Object>> normalizeAggregations(
            List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) return values;
        List<Map<String, Object>> normalized = new ArrayList<>(values.size());
        Set<String> aliases = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> source = values.get(index);
            if (source == null) {
                normalized.add(null);
                continue;
            }
            Map<String, Object> target = new LinkedHashMap<>();
            String function = text(source.get("function"));
            if (function != null) {
                function = function.toLowerCase(Locale.ROOT);
                target.put("function", function);
            }
            String field = text(source.get("field"));
            if (!("count".equals(function) && isProcessInstanceId(field)) && field != null) {
                target.put("field", field);
            }
            String alias = text(source.get("alias"));
            if (alias == null || !AGGREGATION_ALIAS.matcher(alias).matches()
                    || aliases.contains(alias)) {
                alias = uniqueMetricAlias(index + 1, aliases);
            }
            target.put("alias", alias);
            aliases.add(alias);
            normalized.add(target);
        }
        return normalized;
    }

    private static String uniqueMetricAlias(int position, Set<String> aliases) {
        String base = "metric" + position;
        String candidate = base;
        int suffix = 2;
        while (aliases.contains(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static boolean isProcessInstanceId(String value) {
        if (value == null) return false;
        String normalized = value.replaceAll("[\\s_\\-]+", "")
                .toLowerCase(Locale.ROOT);
        return "processinstanceid".equals(normalized) || "流程实例id".equals(normalized);
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalizeRecordMode(String value) {
        String normalized = normalize(value, "PROCESS");
        return switch (normalized) {
            case "流程", "流程去重", "DEDUPLICATED" -> "PROCESS";
            case "办理记录", "操作记录" -> "OPERATION";
            default -> normalized;
        };
    }
}
