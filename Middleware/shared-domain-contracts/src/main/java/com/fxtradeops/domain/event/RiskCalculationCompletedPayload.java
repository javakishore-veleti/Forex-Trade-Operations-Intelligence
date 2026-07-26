package com.fxtradeops.domain.event;

import com.fxtradeops.domain.risk.ContributingFactor;
import com.fxtradeops.domain.risk.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Payload for the RISK_CALCULATION_COMPLETED event — carries the full risk result.
 */
public record RiskCalculationCompletedPayload(
        @NotBlank String tradeId,
        @NotBlank String calculationId,
        @NotBlank String calculationRequestId,
        @NotNull BigDecimal riskAmount,
        @NotBlank String riskCurrency,
        @NotNull RiskLevel riskLevel,
        @NotEmpty List<ContributingFactor> contributingFactors,
        @NotBlank String ruleVersion,
        @NotEmpty List<String> rulesFired,
        @NotNull Instant calculatedAt
) {
}
