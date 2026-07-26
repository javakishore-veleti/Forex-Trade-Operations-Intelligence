package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Payload for the REGIONAL_CLOSE_READY EOD event.
 */
public record RegionalCloseReadyPayload(
        @NotBlank String regionCode,
        @NotNull LocalDate globalBusinessDate,
        @NotNull Instant readyAt,
        @PositiveOrZero int branchCount,
        @PositiveOrZero int completedBranchCount
) {
}
