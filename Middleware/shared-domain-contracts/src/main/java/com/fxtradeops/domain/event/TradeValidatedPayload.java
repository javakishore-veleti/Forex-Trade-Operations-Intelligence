package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the TRADE_VALIDATED event.
 */
public record TradeValidatedPayload(
        @NotBlank String tradeId,
        @NotNull Instant validatedAt
) {
}
