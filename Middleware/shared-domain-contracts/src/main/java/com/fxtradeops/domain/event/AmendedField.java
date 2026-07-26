package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;

/**
 * Describes a single field changed in a trade amendment.
 */
public record AmendedField(
        @NotBlank String fieldName,
        String previousValue,
        String newValue
) {
}
