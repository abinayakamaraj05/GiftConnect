package com.giftconnect.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catches exceptions thrown anywhere in the Category/Product controllers
 * and turns them into consistent JSON error responses, instead of every
 * controller method needing its own try/catch.
 *
 * Client-facing error bodies are intentionally limited to safe, curated text.
 * Unexpected exceptions are logged server-side (with their stack trace) and
 * returned as a generic message, so no internal detail (messages, SQL, class
 * names, stack traces) ever reaches the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Unknown routes and missing static resources (e.g. GET /no/such/route).
     * Spring throws NoResourceFoundException from its static-resource handler;
     * without this specific handler it would fall through to the generic
     * handler below and become a 500. Returned as a flat 404 with no detail so
     * the response cannot reveal filesystem/route internals.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException e) {
        log.warn("No handler/resource found for request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not found"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * Thrown by AccessControl when an authenticated caller lacks the role or
     * ownership required for an action. Always 403 — never 404 — so response
     * codes cannot be used to probe for resources the caller may not see.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateResourceException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * Covers Order/Payment business-rule violations: insufficient stock,
     * invalid quantity, invalid status value, cancelling a delivered order, etc.
     */
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOperation(InvalidOperationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * Triggered by @Valid failing on a @NotBlank / @NotNull / @DecimalMin field.
     * Returns a map of fieldName -> message so the frontend can highlight
     * exactly which field was invalid.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Triggered when a path variable or query parameter cannot be converted to the
     * expected type — e.g. an audit filter like role=NOPE, type=NOT_A_TYPE, or a
     * non-numeric id. Without this handler the generic one below would turn
     * Spring's own 400 into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid value for '" + e.getName() + "': " + e.getValue()));
    }

    /**
     * Triggered when a request body cannot be read — malformed JSON, or a value
     * that does not fit the target type (e.g. {"role":"MANAGER"} for the Role
     * enum, or {"status":"DELETED"}). Reported as 400 rather than 500, so an
     * invalid role/status is explicitly rejected instead of crashing.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Malformed request body or unsupported value"));
    }

    /**
     * Triggered when deleting a category that still has products pointing at it
     * (the FK constraint in the database blocks the delete). We translate the
     * low-level SQL error into a friendly message instead of a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "This action violates a database constraint — " +
                        "e.g. deleting a category that still has products assigned to it."));
    }

    /**
     * Last-resort catch-all for anything not handled above. The real exception
     * (message + stack trace) is logged server-side only; the client receives a
     * stable generic message so internal diagnostics never leak through the API.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
