package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Payload for the REGIONAL_CLOSE_CLOSED EOD event.
 */
public record RegionalCloseClosedPayload(
        @NotBlank String regionCode,
        @NotNull LocalDate globalBusinessDate,
        @NotNull Instant closedAt
) {
}
