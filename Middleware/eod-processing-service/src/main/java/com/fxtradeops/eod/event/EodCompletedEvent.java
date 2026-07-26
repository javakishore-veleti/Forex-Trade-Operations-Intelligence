package com.fxtradeops.eod.event;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain event published when global consolidation succeeds.
 * Shared-contract type: eventId, correlationId, sourceService, occurredAt, businessDate.
 */
public record EodCompletedEvent(
        String eventId,
        String correlationId,
        String sourceService,
        Instant occurredAt,
        LocalDate businessDate
) {
}
