package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Payload for the TRADE_SETTLED event — terminal lifecycle state for successful trades.
 */
public record TradeSettledPayload(
        @NotBlank String tradeId,
        @NotNull Instant settledAt,
        @NotNull LocalDate settlementDate,
        @NotBlank String nostroAccount
) {
}
