package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Detects when the same eventId is observed more than once for a given tradeId.
 * Distinguishes true duplicates (identical payload) from conflicting replays (differing payload).
 */
public class DuplicateEventDetector {

    /**
     * Checks if the arriving event's eventId already exists in the sequence fact.
     *
     * @param fact          current sequence fact
     * @param eventId       eventId of the arriving event
     * @param eventType     type of the arriving event
     * @param correlationId correlation id of the arriving event
     * @param payloadHash   hash of the event payload for comparison
     * @param originalHash  hash of the original event payload (if previously seen)
     * @return anomaly envelope if duplicate or conflicting replay detected
     */
    public Optional<AnomalyEnvelope> detect(
            SequenceFact fact,
            String eventId,
            TradeEventType eventType,
            String correlationId,
            String payloadHash,
            String originalHash
    ) {
        boolean alreadySeen = fact.observedEvents().stream()
                .anyMatch(obs -> obs.eventId().equals(eventId));

        if (!alreadySeen) {
            return Optional.empty();
        }

        // Already seen — determine if true duplicate or conflicting replay
        if (payloadHash.equals(originalHash)) {
            return Optional.of(new AnomalyEnvelope(
                    fact.tradeId(),
                    ViolationType.DUPLICATE_EVENT,
                    Map.of(
                            "duplicateEventId", eventId,
                            "eventType", eventType.name(),
                            "firstSeenAt", fact.observedEvents().stream()
                                    .filter(obs -> obs.eventId().equals(eventId))
                                    .findFirst()
                                    .map(obs -> obs.observedAt().toString())
                                    .orElse("unknown"),
                            "duplicateSeenAt", Instant.now().toString()
                    ),
                    Instant.now(),
                    correlationId,
                    fact
            ));
        } else {
            return Optional.of(new AnomalyEnvelope(
                    fact.tradeId(),
                    ViolationType.CONFLICTING_REPLAY,
                    Map.of(
                            "duplicateEventId", eventId,
                            "eventType", eventType.name(),
                            "originalPayloadHash", originalHash,
                            "conflictingPayloadHash", payloadHash
                    ),
                    Instant.now(),
                    correlationId,
                    fact
            ));
        }
    }
}
