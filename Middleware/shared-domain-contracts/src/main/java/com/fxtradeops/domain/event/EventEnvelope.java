package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * Standard metadata envelope carried by every domain event on the event stream.
 * Provides correlation, deduplication, routing, and schema versioning context.
 */
public record EventEnvelope(
        @NotBlank String eventId,
        @NotNull TradeEventType eventType,
        @Positive int schemaVersion,
        @NotBlank String correlationId,
        @NotBlank String tradeId,
        @NotBlank String sourceService,
        @NotNull Instant occurredAt,
        @NotNull Instant publishedAt
) {
}
