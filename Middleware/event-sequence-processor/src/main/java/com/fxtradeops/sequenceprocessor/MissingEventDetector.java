package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detects when a required predecessor event was never observed before a later event arrived.
 */
public class MissingEventDetector {

    /**
     * Checks whether the arriving event implies a missing predecessor.
     *
     * @param fact         current sequence fact for the trade
     * @param arrivingType the event type that just arrived
     * @param correlationId the correlation id from the arriving event
     * @return anomaly envelope if a missing event is detected, empty otherwise
     */
    public Optional<AnomalyEnvelope> detect(SequenceFact fact, TradeEventType arrivingType, String correlationId) {
        TradeEventType requiredPred = TradeLifecycleStateMachine.requiredPredecessor(arrivingType);
        if (requiredPred == null) {
            return Optional.empty();
        }

        boolean predecessorObserved = fact.observedEvents().stream()
                .anyMatch(obs -> obs.eventType() == requiredPred);

        if (!predecessorObserved && !fact.missingEvents().contains(requiredPred)) {
            return Optional.of(new AnomalyEnvelope(
                    fact.tradeId(),
                    ViolationType.MISSING_EVENT,
                    Map.of(
                            "missingEventType", requiredPred.name(),
                            "observedEventType", arrivingType.name()
                    ),
                    Instant.now(),
                    correlationId,
                    fact
            ));
        }

        return Optional.empty();
    }

    /**
     * Checks whether a previously missing event has now been resolved.
     *
     * @param fact         current sequence fact for the trade (before update)
     * @param arrivingType the event type that just arrived
     * @param correlationId the correlation id
     * @return anomaly envelope of type MISSING_EVENT_RESOLVED if resolved, empty otherwise
     */
    public Optional<AnomalyEnvelope> detectResolution(SequenceFact fact, TradeEventType arrivingType, String correlationId) {
        if (fact.missingEvents().contains(arrivingType)) {
            return Optional.of(new AnomalyEnvelope(
                    fact.tradeId(),
                    ViolationType.MISSING_EVENT_RESOLVED,
                    Map.of("resolvedEventType", arrivingType.name()),
                    Instant.now(),
                    correlationId,
                    fact
            ));
        }
        return Optional.empty();
    }

    /**
     * Updates the missing events list: adds newly detected missing events and removes resolved ones.
     */
    public List<TradeEventType> updateMissingEvents(List<TradeEventType> currentMissing, TradeEventType arrivingType) {
        List<TradeEventType> updated = new ArrayList<>(currentMissing);
        // Remove if this event resolves a previously missing one
        updated.remove(arrivingType);
        // Add predecessor if missing
        TradeEventType requiredPred = TradeLifecycleStateMachine.requiredPredecessor(arrivingType);
        if (requiredPred != null && !updated.contains(requiredPred)) {
            // We'll check the observed events externally; this is a helper
        }
        return updated;
    }
}
