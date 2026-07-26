package com.fxtradeops.eod.web;

import com.fxtradeops.eod.api.EodCommandController;
import com.fxtradeops.eod.api.dto.NotReadyConflict;
import com.fxtradeops.eod.application.ConsolidationService;
import com.fxtradeops.eod.application.ExceptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Global exception handler — returns structured error envelopes without stack traces (GP-Rq-3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(EodCommandController.UnknownRegionException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownRegion(EodCommandController.UnknownRegionException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ExceptionService.BlockerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBlockerNotFound(ExceptionService.BlockerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ConsolidationService.NotReadyException.class)
    public ResponseEntity<NotReadyConflict> handleNotReady(ConsolidationService.NotReadyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new NotReadyConflict(
                409,
                "Conflict",
                ex.getMessage(),
                ex.getNotReadyRegions(),
                ex.getBlockers(),
                Instant.now().toString()
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409,
                "error", "Conflict",
                "message", "Concurrent modification detected, please retry",
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", "An unexpected error occurred",
                "timestamp", Instant.now().toString()
        ));
    }
}
