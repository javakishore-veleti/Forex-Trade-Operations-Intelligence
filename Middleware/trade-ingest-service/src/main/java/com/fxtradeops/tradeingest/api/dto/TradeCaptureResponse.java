package com.fxtradeops.tradeingest.api.dto;

/**
 * Outbound confirmation record returned after a successful trade capture.
 */
public record TradeCaptureResponse(
        String tradeId,
        String correlationId,
        String status
) {
}
