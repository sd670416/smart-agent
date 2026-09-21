package com.smart.agent.tool.approval;

import java.util.List;
import java.util.Map;
import java.util.Locale;

public record ApprovalQueryInput(String scope, String visibility, String personKeyword,
                                 List<String> select, Map<String, Object> filter,
                                 List<String> groupBy, List<Map<String, Object>> aggregations,
                                 List<Map<String, Object>> orderBy, Integer page,
                                 Integer pageSize, String recordMode, String processType) {
    public ApprovalQueryInput {
        scope = normalizeScope(scope);
        visibility = normalizeVisibility(visibility);
        recordMode = normalizeRecordMode(recordMode);
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

    private static String normalizeRecordMode(String value) {
        String normalized = normalize(value, "PROCESS");
        return switch (normalized) {
            case "流程", "流程去重", "DEDUPLICATED" -> "PROCESS";
            case "办理记录", "操作记录" -> "OPERATION";
            default -> normalized;
        };
    }
}
