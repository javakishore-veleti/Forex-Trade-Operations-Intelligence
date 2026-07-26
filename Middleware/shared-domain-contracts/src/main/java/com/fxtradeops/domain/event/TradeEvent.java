package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable domain event emitted during trade processing.
 */
public record TradeEvent(
        @NotBlank String eventId,
        @NotBlank String tradeId,
        @NotBlank String correlationId,
        @NotNull TradeEventType eventType,
        @NotNull Instant occurredAt,
        @NotNull Long sequenceNumber,
        @NotBlank String sourceService,
        @NotNull Map<String, Object> payload
) {
}
