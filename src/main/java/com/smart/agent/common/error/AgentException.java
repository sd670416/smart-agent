package com.smart.agent.common.error;

import org.springframework.http.HttpStatus;

public class AgentException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AgentException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static AgentException unauthorized() {
        return new AgentException(
                "AGENT_UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Valid agent context is required");
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
