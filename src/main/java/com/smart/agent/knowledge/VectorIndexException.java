package com.smart.agent.knowledge;

public final class VectorIndexException extends RuntimeException {
    private final String code;

    public VectorIndexException(String code, String message) {
        super(message);
        this.code = code;
    }

    public VectorIndexException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
