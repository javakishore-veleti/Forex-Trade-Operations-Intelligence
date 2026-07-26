package com.fxtradeops.tradeingest.web;

import com.fxtradeops.tradeingest.domain.DomainValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler producing structured error envelopes.
 * Handles: MethodArgumentNotValidException (400), DomainValidationException (400),
 * DataIntegrityViolationException (409), and generic Exception (500).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message",
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .toList();

        return ResponseEntity.badRequest().body(buildErrorBody(400, errors));
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<Map<String, Object>> handleDomainValidation(DomainValidationException ex) {
        return ResponseEntity.badRequest().body(buildErrorBody(400, ex.getErrors()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                buildErrorBody(409, List.of(Map.of("field", "trade", "message", "duplicate or constraint violation"))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                buildErrorBody(500, List.of(Map.of("field", "unknown", "message", "internal server error"))));
    }

    private Map<String, Object> buildErrorBody(int status, List<Map<String, String>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("timestamp", Instant.now().toString());
        body.put("errors", errors);
        return body;
    }
}
