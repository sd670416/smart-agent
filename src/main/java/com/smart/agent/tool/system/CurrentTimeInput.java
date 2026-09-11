package com.smart.agent.tool.system;

public record CurrentTimeInput(String timezone) {
    public CurrentTimeInput {
        timezone = timezone == null || timezone.isBlank() ? null : timezone.trim();
    }
}
