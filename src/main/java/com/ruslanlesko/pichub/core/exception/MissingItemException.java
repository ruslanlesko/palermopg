package com.ruslanlesko.pichub.core.exception;

public class MissingItemException extends RuntimeException {
    public MissingItemException() {
    }

    public MissingItemException(String message) {
        super(message);
    }
}
