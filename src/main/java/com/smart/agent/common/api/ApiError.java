package com.smart.agent.common.api;

public record ApiError(String code, String message, String traceId) {
}
