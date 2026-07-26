package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the TRADE_ALLOCATED event.
 */
public record TradeAllocatedPayload(
        @NotBlank String tradeId,
        @NotNull Instant allocatedAt
) {
}
