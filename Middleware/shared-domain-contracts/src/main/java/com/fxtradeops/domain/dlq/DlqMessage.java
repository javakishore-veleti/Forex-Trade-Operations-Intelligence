package com.fxtradeops.domain.dlq;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * Immutable representation of a dead-lettered message — wraps the original payload
 * together with quarantine header metadata.
 */
public record DlqMessage(
        @NotBlank String originTopic,
        @PositiveOrZero int originPartition,
        @PositiveOrZero long originOffset,
        @NotBlank String failureReason,
        @PositiveOrZero int failureCount,
        @NotNull Instant failureTimestamp,
        boolean poisonFlag,
        @NotBlank String correlationId,
        @NotBlank String tradeId,
        @NotNull byte[] originalPayload,
        @NotNull PoisonMessageStatus status
) {
}
