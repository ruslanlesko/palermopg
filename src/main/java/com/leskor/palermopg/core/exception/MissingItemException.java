package com.leskor.palermopg.core.exception;

public class MissingItemException extends RuntimeException {
    public MissingItemException() {
    }

    public MissingItemException(String message) {
        super(message);
    }
}
