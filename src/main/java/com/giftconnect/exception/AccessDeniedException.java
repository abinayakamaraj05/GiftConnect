package com.giftconnect.exception;

/**
 * Thrown when an authenticated caller is not permitted to perform an action
 * (wrong role, or not the owner of the target resource). Mapped to HTTP 403
 * by GlobalExceptionHandler.
 *
 * Deliberately a distinct type from ResourceNotFoundException (404) so that a
 * caller cannot use response codes to probe whether a resource exists: an
 * unauthorized call is always a 403, never a 404-for-someone-else's-record.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
