package com.fxtradeops.domain.risk;

import com.fxtradeops.domain.reference.RegionCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request to compute risk for a trade.
 */
public record RiskCalculationRequest(
        @NotBlank String tradeId,
        @NotBlank String correlationId,
        @NotBlank String requestId,
        @NotNull RegionCode regionCode,
        @NotBlank String tradingBookId,
        @NotNull Instant requestedAt,
        @Min(1) int priority
) {
}
