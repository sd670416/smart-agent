package com.smart.agent.chat;

import com.smart.agent.run.AgentRunStatus;
import java.util.Map;

public record ChatEvent(
        String type,
        String runId,
        long sequence,
        String traceId,
        String messageId,
        String code,
        String text,
        String toolKey,
        Map<String, Object> data) {

    public ChatEvent(String type, String runId, String traceId, String messageId, String code,
            String text, String toolKey, Map<String, Object> data) {
        this(type, runId, 0L, traceId, messageId, code, text, toolKey, data);
    }

    static ChatEvent messageStart(String runId, String traceId, String messageId) {
        return new ChatEvent("message_start", runId, traceId, messageId, null, null, null, Map.of());
    }

    static ChatEvent status(String runId, String traceId, AgentRunStatus status) {
        return new ChatEvent("status", runId, traceId, null, status.name(), null, null, Map.of());
    }

    static ChatEvent toolStart(String runId, String traceId, String toolKey) {
        return new ChatEvent("tool_start", runId, traceId, null, null, null, toolKey, Map.of());
    }

    static ChatEvent toolResult(String runId, String traceId, String toolKey) {
        return new ChatEvent("tool_result", runId, traceId, null, "SUCCEEDED", null, toolKey, Map.of());
    }

    static ChatEvent citation(String runId, String traceId, Map<String, Object> data) {
        return new ChatEvent("citation", runId, traceId, null, null, null, null, Map.copyOf(data));
    }

    static ChatEvent delta(String runId, String traceId, String text) {
        return new ChatEvent("message_delta", runId, traceId, null, null, text, null, Map.of());
    }

    static ChatEvent messageEnd(String runId, String traceId, String messageId) {
        return new ChatEvent("message_end", runId, traceId, messageId, null, null, null, Map.of());
    }

    static ChatEvent error(String runId, String traceId, String code) {
        return error(runId, traceId, code, "Agent request failed");
    }

    static ChatEvent error(String runId, String traceId, String code, String text) {
        return new ChatEvent("error", runId, traceId, null, code, text, null, Map.of());
    }
}
