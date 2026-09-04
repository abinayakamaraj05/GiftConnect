package com.giftconnect.exception;

/**
 * Thrown when trying to create a category whose name already exists.
 * Caught by GlobalExceptionHandler and turned into HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
