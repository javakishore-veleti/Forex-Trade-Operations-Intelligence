package com.fxtradeops.riskcalc.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for risk aggregation queries.
 */
public record AggregationResponse(
        String scopeType,
        String scopeId,
        BigDecimal totalRiskAmount,
        String riskCurrency,
        int tradeCount,
        Instant lastUpdatedAt
) {
}
