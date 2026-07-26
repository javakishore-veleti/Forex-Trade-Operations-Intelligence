package com.fxtradeops.eod.api.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * View of the consolidation status for a business date.
 */
public record ConsolidationView(
        LocalDate businessDate,
        String status,
        String contributingRegions,
        String appliedExceptions,
        Instant consolidatedAt
) {
}
