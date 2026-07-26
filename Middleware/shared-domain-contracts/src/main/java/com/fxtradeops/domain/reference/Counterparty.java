package com.fxtradeops.domain.reference;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * An external or internal party to a trade.
 */
public record Counterparty(
        @NotBlank String counterpartyId,
        @NotBlank String counterpartyName,
        @NotNull CounterpartyType counterpartyType,
        @NotNull RegionCode regionCode,
        boolean isActive,
        @NotBlank String creditRating
) {
}
