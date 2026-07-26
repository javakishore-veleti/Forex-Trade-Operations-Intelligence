package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the TRADE_FAILED event — terminal lifecycle state for failed trades.
 */
public record TradeFailedPayload(
        @NotBlank String tradeId,
        @NotNull Instant failedAt,
        @NotBlank String failureReason,
        @NotBlank String failedStage
) {
}
