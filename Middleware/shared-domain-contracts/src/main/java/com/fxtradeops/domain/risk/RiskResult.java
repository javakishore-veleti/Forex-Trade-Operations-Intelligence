package com.fxtradeops.domain.risk;

import com.fxtradeops.domain.reference.RegionCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Output of a risk calculation for a single trade.
 */
public record RiskResult(
        @NotBlank String tradeId,
        @NotBlank String calculationId,
        @NotNull @Positive BigDecimal riskAmount,
        @NotBlank String riskCurrency,
        @NotNull RegionCode regionCode,
        @NotBlank String tradingBookId,
        @NotNull Instant calculatedAt,
        @NotBlank String ruleVersion,
        @NotNull RiskLevel riskLevel
) {
}
