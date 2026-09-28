package com.smart.agent.clarification;

/** Stable domain failure for invalid, expired or already-consumed clarification choices. */
public class PendingClarificationException extends RuntimeException {
    public PendingClarificationException(String message) {
        super(message);
    }
}
