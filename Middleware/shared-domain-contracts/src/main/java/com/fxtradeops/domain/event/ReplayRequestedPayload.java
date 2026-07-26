package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload for the REPLAY_REQUESTED event — triggers reprocessing of a trade's event history.
 * Requires a valid approvalReference from the HITL gate; events without one are
 * treated as unauthorized and routed to the DLQ.
 */
public record ReplayRequestedPayload(
        @NotBlank String tradeId,
        @NotNull TradeEventType replayFromEventType,
        @NotBlank String requestedBy,
        @NotNull Instant requestedAt,
        @NotBlank String approvalReference
) {
}
