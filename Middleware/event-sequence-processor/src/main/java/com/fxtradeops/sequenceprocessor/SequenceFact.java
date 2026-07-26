package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;

import java.time.Instant;
import java.util.List;

/**
 * Per-tradeId running state maintained in the Kafka Streams state store.
 * Tracks observed events, expected transitions, and detected anomalies.
 */
public record SequenceFact(
        String tradeId,
        List<ObservedEvent> observedEvents,
        List<TradeEventType> expectedNextEvents,
        List<TradeEventType> missingEvents,
        List<String> duplicateEventIds,
        List<String> sequenceViolations,
        TradeEventType lastStatus,
        Instant lastUpdatedAt
) {

    /**
     * Record of a single observed event within a trade's sequence.
     */
    public record ObservedEvent(
            String eventId,
            TradeEventType eventType,
            Instant observedAt
    ) {
    }
}
