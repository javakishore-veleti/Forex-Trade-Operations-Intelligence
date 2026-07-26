package com.fxtradeops.riskcalc.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for on-demand risk calculation via POST /api/v1/risk/calculate.
 */
public record RiskCalculateRequest(
        @NotBlank String requestId,
        @NotBlank String tradeId,
        @NotBlank String regionCode,
        @NotBlank String tradingBookId,
        @NotBlank String currencyPairCode,
        @NotBlank String baseCurrency,
        @NotBlank String quoteCurrency,
        @NotNull @Positive BigDecimal notionalAmount,
        @NotBlank String notionalCurrency
) {
}
