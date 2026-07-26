package com.fxtradeops.domain.event;

import com.fxtradeops.domain.risk.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload for the TRADE_RISK_CALCULATED event — carries the computed risk result.
 */
public record TradeRiskCalculatedPayload(
        @NotBlank String tradeId,
        @NotBlank String calculationId,
        @NotNull BigDecimal riskAmount,
        @NotBlank String riskCurrency,
        @NotNull RiskLevel riskLevel,
        @NotBlank String ruleVersion
) {
}
