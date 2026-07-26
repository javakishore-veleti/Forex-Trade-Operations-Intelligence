package com.fxtradeops.domain.event;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload for the GLOBAL_CONSOLIDATION_COMPLETED EOD event.
 * Published exactly once per globalBusinessDate.
 */
public record GlobalConsolidationCompletedPayload(
        @NotNull LocalDate globalBusinessDate,
        @NotNull Instant consolidatedAt,
        @NotEmpty List<RegionSummaryEntry> regionSummary
) {
}
