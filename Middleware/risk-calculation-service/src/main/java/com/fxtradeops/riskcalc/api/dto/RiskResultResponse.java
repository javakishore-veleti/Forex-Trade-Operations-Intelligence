package com.fxtradeops.riskcalc.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response DTO for risk calculation results.
 */
public record RiskResultResponse(
        String calculationId,
        String tradeId,
        BigDecimal riskAmount,
        String riskCurrency,
        String regionCode,
        String tradingBookId,
        Instant calculatedAt,
        String ruleVersion,
        String riskLevel,
        List<String> rulesFired,
        String contributingFactors
) {
}
