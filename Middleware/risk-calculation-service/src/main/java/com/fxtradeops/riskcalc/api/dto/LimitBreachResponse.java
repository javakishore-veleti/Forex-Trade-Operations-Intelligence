package com.fxtradeops.riskcalc.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for limit breach queries.
 */
public record LimitBreachResponse(
        String breachId,
        String calculationId,
        String scopeType,
        String scopeId,
        BigDecimal limitAmount,
        BigDecimal observedAmount,
        Instant detectedAt
) {
}
