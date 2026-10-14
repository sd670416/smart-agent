package com.smart.agent.question;

public final class QuestionSnapshot {
    private QuestionSnapshot() {}
    public static boolean automatic(String status) { return "FAILED".equals(status) || "TIMEOUT".equals(status); }
    public static String safe(String value) {
        if (value == null) return "";
        String result = value.replaceAll("(?i)([\"']?(?:password|passwd|secret|token|api[_-]?key|signature|authorization|cookie)[\"']?\\s*[:=]\\s*)\"[^\"]*\"", "$1\"[REDACTED]\"")
                .replaceAll("(?i)([\"']?(?:password|passwd|secret|token|api[_-]?key|signature|authorization|cookie)[\"']?\\s*[:=]\\s*)'[^']*'", "$1'[REDACTED]'")
                .replaceAll("(?i)sk-[a-z0-9_-]{12,}", "[REDACTED]")
                .replaceAll("(?i)(Bearer\\s+)[^\\s\"',}]+", "$1[REDACTED]")
                .replaceAll("(?i)([\"']?(?:password|passwd|secret|token|api[_-]?key|signature|authorization|cookie)[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+", "$1[REDACTED]")
                .replaceAll("https?://[^\\s\"<>]+", "[URL]");
        return result.substring(0, Math.min(result.length(), 2000));
    }
}
