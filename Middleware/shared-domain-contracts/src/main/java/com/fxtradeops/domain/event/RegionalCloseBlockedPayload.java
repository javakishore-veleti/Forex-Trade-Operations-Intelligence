package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Payload for the REGIONAL_CLOSE_BLOCKED EOD event.
 */
public record RegionalCloseBlockedPayload(
        @NotBlank String regionCode,
        @NotNull LocalDate globalBusinessDate,
        @NotNull Instant blockedAt,
        @NotBlank String blockerCode,
        @NotBlank String blockerDescription
) {
}
