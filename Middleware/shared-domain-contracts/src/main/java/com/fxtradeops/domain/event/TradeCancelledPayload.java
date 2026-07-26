package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the TRADE_CANCELLED event — terminal lifecycle state for cancelled trades.
 */
public record TradeCancelledPayload(
        @NotBlank String tradeId,
        @NotNull Instant cancelledAt,
        @NotBlank String cancelledBy,
        @NotBlank String cancellationReason
) {
}
