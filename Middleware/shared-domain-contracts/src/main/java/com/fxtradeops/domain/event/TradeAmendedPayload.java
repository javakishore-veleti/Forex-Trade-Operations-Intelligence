package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Payload for the TRADE_AMENDED event — carries the list of fields that changed.
 */
public record TradeAmendedPayload(
        @NotBlank String tradeId,
        @NotNull Instant amendedAt,
        @NotBlank String amendedBy,
        @NotEmpty List<AmendedField> amendedFields,
        @NotBlank String amendmentReason
) {
}
