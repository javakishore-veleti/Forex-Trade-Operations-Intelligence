package com.fxtradeops.tradeingest.api;

import com.fxtradeops.tradeingest.api.dto.TradeCaptureResponse;
import com.fxtradeops.tradeingest.api.dto.TradeRequest;
import com.fxtradeops.tradeingest.application.TradeCaptureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for trade capture.
 * POST /api/v1/trades — accepts trade requests, validates, captures, and returns confirmation.
 */
@RestController
@RequestMapping("/api/v1/trades")
public class TradeIngestController {

    private final TradeCaptureService tradeCaptureService;

    public TradeIngestController(TradeCaptureService tradeCaptureService) {
        this.tradeCaptureService = tradeCaptureService;
    }

    @PostMapping
    public ResponseEntity<TradeCaptureResponse> captureTrade(
            @RequestHeader(value = "X-Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody TradeRequest request) {

        // Resolve correlation ID
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        TradeCaptureService.CaptureResult result =
                tradeCaptureService.capture(request, idempotencyKey, correlationId);

        if (result.replay()) {
            return ResponseEntity.ok(result.response());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }
}
