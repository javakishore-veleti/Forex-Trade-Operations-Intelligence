package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Detects when an event arrives for a lifecycle status the trade has already surpassed.
 */
public class OutOfOrderEventDetector {

    /**
     * Checks whether the arriving event type is earlier in the lifecycle than the current status.
     *
     * @param fact          current sequence fact
     * @param arrivingType  the event type that just arrived
     * @param correlationId the correlation id
     * @return anomaly envelope if out-of-order detected, empty otherwise
     */
    public Optional<AnomalyEnvelope> detect(SequenceFact fact, TradeEventType arrivingType, String correlationId) {
        if (fact.lastStatus() == null) {
            return Optional.empty();
        }

        int currentOrdinal = TradeLifecycleStateMachine.lifecycleOrdinal(fact.lastStatus());
        int arrivingOrdinal = TradeLifecycleStateMachine.lifecycleOrdinal(arrivingType);

        // Only applies to lifecycle events (ordinal >= 0) arriving before current status
        if (arrivingOrdinal >= 0 && currentOrdinal >= 0 && arrivingOrdinal < currentOrdinal) {
            return Optional.of(new AnomalyEnvelope(
                    fact.tradeId(),
                    ViolationType.OUT_OF_ORDER_EVENT,
                    Map.of(
                            "arrivingEventType", arrivingType.name(),
                            "arrivingEventStatus", arrivingType.name(),
                            "currentFactStatus", fact.lastStatus().name()
                    ),
                    Instant.now(),
                    correlationId,
                    fact
            ));
        }

        return Optional.empty();
    }
}
