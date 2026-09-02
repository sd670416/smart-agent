package com.smart.agent.model;

public sealed interface ModelEvent {
    record TextDelta(String text) implements ModelEvent {}

    record ToolRequested(String callId, String toolKey, String argumentsJson) implements ModelEvent {}

    record Completed(String text, int inputTokens, int outputTokens) implements ModelEvent {}

    record Failed(String code, String message) implements ModelEvent {}
}
