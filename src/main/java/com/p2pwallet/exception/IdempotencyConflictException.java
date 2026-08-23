package com.p2pwallet.exception;

/**
 * Thrown when an idempotency key is reused with a different request body.
 * Maps to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
