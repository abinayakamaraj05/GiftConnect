package com.giftconnect.exception;

/**
 * Thrown when a category or product id doesn't exist.
 * Caught by GlobalExceptionHandler and turned into HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
