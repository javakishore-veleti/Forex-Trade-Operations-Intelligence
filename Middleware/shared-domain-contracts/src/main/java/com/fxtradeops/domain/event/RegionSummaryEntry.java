package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotBlank;

/**
 * Summary status entry for a single region within a global consolidation event.
 */
public record RegionSummaryEntry(
        @NotBlank String regionCode,
        @NotBlank String status
) {
}
