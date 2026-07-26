package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the RISK_CALCULATION_FAILED event.
 */
public record RiskCalculationFailedPayload(
        @NotBlank String tradeId,
        @NotBlank String calculationRequestId,
        @NotBlank String failureReason,
        @NotNull Instant failedAt
) {
}
