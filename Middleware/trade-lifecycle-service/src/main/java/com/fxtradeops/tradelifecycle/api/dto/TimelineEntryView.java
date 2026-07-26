package com.fxtradeops.tradelifecycle.api.dto;

import java.time.Instant;

/**
 * A single entry in the trade lifecycle timeline.
 */
public record TimelineEntryView(
        String eventId,
        String eventType,
        String fromStatus,
        String toStatus,
        boolean rejected,
        boolean noop,
        boolean orphan,
        String sourceService,
        Instant occurredAt,
        Instant recordedAt
) {
}
