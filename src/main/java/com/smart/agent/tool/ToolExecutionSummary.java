package com.smart.agent.tool;

public record ToolExecutionSummary(
        String toolKey, ToolRisk risk, ToolExecutionOutcome outcome, long durationMillis, int resultSizeBytes) {
    public ToolExecutionSummary {
        if (toolKey == null || toolKey.isBlank()) {
            throw new IllegalArgumentException("toolKey must not be blank");
        }
        if (risk == null || outcome == null || durationMillis < 0 || resultSizeBytes < 0) {
            throw new IllegalArgumentException("Invalid tool execution summary");
        }
    }
}
