package com.craftguessr.ai;

/** Thrown when an AI provider call fails (transport, HTTP status or unusable payload). */
public class AiException extends Exception {
    private final int httpStatus;

    public AiException(String message) {
        this(message, -1, null);
    }

    public AiException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public AiException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** True when retrying the exact same request may succeed (429 / 5xx / network). */
    public boolean retryable() {
        return httpStatus < 0 || httpStatus == 408 || httpStatus == 429 || httpStatus >= 500;
    }
}
