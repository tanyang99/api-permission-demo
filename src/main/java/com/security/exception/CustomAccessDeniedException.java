package com.security.exception;

public class CustomAccessDeniedException extends RuntimeException {

    public CustomAccessDeniedException() {
    }

    public CustomAccessDeniedException(String message) {
        super(message);
    }

    public CustomAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }

    public CustomAccessDeniedException(Throwable cause) {
        super(cause);
    }

    public CustomAccessDeniedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}