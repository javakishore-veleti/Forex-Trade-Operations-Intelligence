package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines the valid state transitions for a trade lifecycle.
 * Used to determine expected next events and detect out-of-order arrivals.
 */
public final class TradeLifecycleStateMachine {

    private TradeLifecycleStateMachine() {
    }

    /** Valid transitions: from a given event type, which event types may follow. */
    private static final Map<TradeEventType, Set<TradeEventType>> TRANSITIONS = Map.ofEntries(
            Map.entry(TradeEventType.TRADE_CAPTURED, Set.of(
                    TradeEventType.TRADE_VALIDATED, TradeEventType.TRADE_FAILED, TradeEventType.TRADE_CANCELLED)),
            Map.entry(TradeEventType.TRADE_VALIDATED, Set.of(
                    TradeEventType.TRADE_ENRICHED, TradeEventType.TRADE_FAILED, TradeEventType.TRADE_CANCELLED)),
            Map.entry(TradeEventType.TRADE_ENRICHED, Set.of(
                    TradeEventType.TRADE_RISK_CALCULATED, TradeEventType.TRADE_FAILED, TradeEventType.TRADE_CANCELLED)),
            Map.entry(TradeEventType.TRADE_RISK_CALCULATED, Set.of(
                    TradeEventType.TRADE_BOOKED, TradeEventType.TRADE_FAILED, TradeEventType.TRADE_CANCELLED)),
            Map.entry(TradeEventType.TRADE_BOOKED, Set.of(
                    TradeEventType.TRADE_ALLOCATED, TradeEventType.TRADE_FAILED, TradeEventType.TRADE_CANCELLED)),
            Map.entry(TradeEventType.TRADE_ALLOCATED, Set.of(
                    TradeEventType.TRADE_CONFIRMED, TradeEventType.TRADE_FAILED, TradeEventType.TRADE_CANCELLED)),
            Map.entry(TradeEventType.TRADE_CONFIRMED, Set.of(
                    TradeEventType.TRADE_SETTLED, TradeEventType.TRADE_FAILED, TradeEventType.TRADE_CANCELLED)),
            Map.entry(TradeEventType.TRADE_SETTLED, Set.of()),
            Map.entry(TradeEventType.TRADE_FAILED, Set.of()),
            Map.entry(TradeEventType.TRADE_CANCELLED, Set.of())
    );

    /** Ordered lifecycle sequence (excluding terminal/branching states). */
    private static final List<TradeEventType> LIFECYCLE_ORDER = List.of(
            TradeEventType.TRADE_CAPTURED,
            TradeEventType.TRADE_VALIDATED,
            TradeEventType.TRADE_ENRICHED,
            TradeEventType.TRADE_RISK_CALCULATED,
            TradeEventType.TRADE_BOOKED,
            TradeEventType.TRADE_ALLOCATED,
            TradeEventType.TRADE_CONFIRMED,
            TradeEventType.TRADE_SETTLED
    );

    /**
     * Returns the set of valid next event types from the given current status.
     */
    public static Set<TradeEventType> expectedNext(TradeEventType current) {
        return TRANSITIONS.getOrDefault(current, Set.of());
    }

    /**
     * Returns the ordinal position of an event type in the lifecycle.
     * Returns -1 for non-lifecycle events (e.g., TRADE_AMENDED) or null.
     */
    public static int lifecycleOrdinal(TradeEventType eventType) {
        if (eventType == null) {
            return -1;
        }
        return LIFECYCLE_ORDER.indexOf(eventType);
    }

    /**
     * Returns true if the event type is a terminal state (no further transitions).
     */
    public static boolean isTerminal(TradeEventType eventType) {
        return eventType == TradeEventType.TRADE_SETTLED
                || eventType == TradeEventType.TRADE_CANCELLED
                || eventType == TradeEventType.TRADE_FAILED;
    }

    /**
     * Returns the required predecessor for an event type, or null if it can be the first event.
     */
    public static TradeEventType requiredPredecessor(TradeEventType eventType) {
        int ordinal = lifecycleOrdinal(eventType);
        if (ordinal <= 0) {
            return null;
        }
        return LIFECYCLE_ORDER.get(ordinal - 1);
    }
}
