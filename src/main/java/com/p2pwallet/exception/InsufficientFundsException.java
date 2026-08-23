package com.p2pwallet.exception;

/**
 * Thrown when a transfer would overdraw the sender's wallet.
 */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
