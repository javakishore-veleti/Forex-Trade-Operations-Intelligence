package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the TRADE_CONFIRMED event.
 */
public record TradeConfirmedPayload(
        @NotBlank String tradeId,
        @NotNull Instant confirmedAt,
        @NotBlank String counterpartyId
) {
}
