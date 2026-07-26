package com.fxtradeops.domain.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Identifies the two ISO-4217 currencies involved in a trade and their combined pair code.
 */
public record CurrencyPair(
        @NotBlank @Size(min = 3, max = 3) String baseCurrency,
        @NotBlank @Size(min = 3, max = 3) String quoteCurrency,
        @NotBlank String pairCode
) {
}
