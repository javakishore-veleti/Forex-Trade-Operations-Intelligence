package com.fxtradeops.domain.risk;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A named component of a risk calculation.
 */
public record ContributingFactor(
        @NotBlank String factorName,
        @NotNull @Positive BigDecimal contributionAmount,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
