package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the TRADE_ENRICHED event.
 */
public record TradeEnrichedPayload(
        @NotBlank String tradeId,
        @NotNull Instant enrichedAt,
        @NotBlank String marketDataSnapshotId
) {
}
