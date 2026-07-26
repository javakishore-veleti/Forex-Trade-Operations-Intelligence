package com.fxtradeops.calendar.web;

import com.fxtradeops.calendar.domain.UnknownRegionCalendarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler producing structured error envelopes.
 * 400 (invalid region/params), 404 (unknown region calendar), 500 (unexpected errors).
 * No stack traces in the response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnknownRegionCalendarException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownRegionCalendar(UnknownRegionCalendarException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorBody(404, List.of(Map.of("field", "region", "message", ex.getMessage()))));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(buildErrorBody(400, List.of(Map.of("field", "parameter", "message", ex.getMessage()))));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return ResponseEntity.badRequest()
                .body(buildErrorBody(400, List.of(Map.of("field", ex.getName(), "message", message))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(buildErrorBody(400, List.of(Map.of("field", ex.getParameterName(), "message", ex.getMessage()))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorBody(500, List.of(Map.of("field", "unknown", "message", "internal server error"))));
    }

    private Map<String, Object> buildErrorBody(int status, List<Map<String, String>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("timestamp", Instant.now().toString());
        body.put("errors", errors);
        return body;
    }
}
