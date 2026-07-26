package com.fxtradeops.domain.reference;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.ZoneId;

/**
 * Geographic or operational region supported by the platform.
 */
public record Region(
        @NotNull RegionCode regionCode,
        @NotBlank String regionName,
        @NotNull ZoneId timezone,
        @NotBlank @Size(min = 3, max = 3) String baseCurrency,
        boolean isActive
) {
}
