package com.giftconnect.exception;

/**
 * Covers every "this request is well-formed but not allowed right now"
 * case for Order/Payment: insufficient stock, invalid quantity, invalid
 * status value, cancelling a non-cancellable order, invalid payment
 * method, etc. Deliberately ONE class instead of five near-identical
 * ones — keeps the exception layer simple, as the project already does
 * with ResourceNotFoundException / DuplicateResourceException.
 * Caught by GlobalExceptionHandler and turned into HTTP 400.
 */
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
