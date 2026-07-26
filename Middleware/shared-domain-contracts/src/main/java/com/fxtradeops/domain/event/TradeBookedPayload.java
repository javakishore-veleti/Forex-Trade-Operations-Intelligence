package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Payload for the TRADE_BOOKED event.
 */
public record TradeBookedPayload(
        @NotBlank String tradeId,
        @NotNull Instant bookedAt,
        @NotNull LocalDate bookingDate,
        @NotBlank String regionCode
) {
}
