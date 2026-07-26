package com.fxtradeops.domain.event;

import com.fxtradeops.domain.trade.CurrencyPair;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for the RISK_CALCULATION_REQUESTED event — triggers asynchronous risk computation.
 */
public record RiskCalculationRequestedPayload(
        @NotBlank String tradeId,
        @NotBlank String calculationRequestId,
        @NotNull CurrencyPair currencyPair,
        @NotNull @Positive BigDecimal notionalAmount,
        @NotBlank String notionalCurrency,
        @NotBlank String regionCode,
        @NotBlank String tradingBookId,
        @NotBlank String marketDataSnapshotId,
        @NotNull Instant requestedAt
) {
}
