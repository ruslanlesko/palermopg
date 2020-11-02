package com.ruslanlesko.palermopg.core.exception;

public class StorageLimitException extends RuntimeException {
    private static final int CODE = 1;
    private static final String MESSAGE = "Storage limit has been reached";

    public StorageLimitException() {
        super(MESSAGE);
    }

    public String json() {
        return String.format("{\"code\":%d,\"message\":\"%s\"}", CODE, MESSAGE);
    }
}
