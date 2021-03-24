package com.leskor.palermopg.exception;

public class AuthorizationException extends RuntimeException {

    public AuthorizationException(String message) {
        super(message);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof AuthorizationException) {
            return ((AuthorizationException) other).getMessage().equals(this.getMessage());
        }
        return false;
    }
}
