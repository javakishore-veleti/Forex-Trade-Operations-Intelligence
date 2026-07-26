package com.fxtradeops.tradelifecycle.domain;

import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.trade.TradeStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic state machine for FX trade lifecycle.
 * Immutable transition table — no rules engine.
 */
public final class StateMachine {

    /**
     * Permitted transitions: each key maps to the set of statuses it can transition to.
     */
    public static final Map<TradeStatus, Set<TradeStatus>> PERMITTED;

    /**
     * Maps each TradeEventType to the TradeStatus it induces.
     */
    public static final Map<TradeEventType, TradeStatus> EVENT_TO_STATUS;

    /**
     * Terminal statuses from which no further forward transition is allowed.
     */
    public static final Set<TradeStatus> TERMINAL = EnumSet.of(
            TradeStatus.SETTLED, TradeStatus.CANCELLED, TradeStatus.FAILED
    );

    /**
     * The expected lifecycle sequence (happy path) for display purposes.
     */
    public static final List<TradeStatus> EXPECTED_LIFECYCLE = List.of(
            TradeStatus.CAPTURED,
            TradeStatus.VALIDATED,
            TradeStatus.ENRICHED,
            TradeStatus.RISK_CALCULATED,
            TradeStatus.BOOKED,
            TradeStatus.ALLOCATED,
            TradeStatus.CONFIRMED,
            TradeStatus.SETTLED
    );

    static {
        EnumMap<TradeStatus, Set<TradeStatus>> map = new EnumMap<>(TradeStatus.class);
        map.put(TradeStatus.CAPTURED, EnumSet.of(
                TradeStatus.VALIDATED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED));
        map.put(TradeStatus.VALIDATED, EnumSet.of(
                TradeStatus.ENRICHED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED));
        map.put(TradeStatus.ENRICHED, EnumSet.of(
                TradeStatus.RISK_CALCULATED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED));
        map.put(TradeStatus.RISK_CALCULATED, EnumSet.of(
                TradeStatus.BOOKED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED));
        map.put(TradeStatus.BOOKED, EnumSet.of(
                TradeStatus.ALLOCATED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED));
        map.put(TradeStatus.ALLOCATED, EnumSet.of(
                TradeStatus.CONFIRMED, TradeStatus.CANCELLED, TradeStatus.FAILED));
        map.put(TradeStatus.CONFIRMED, EnumSet.of(
                TradeStatus.SETTLED, TradeStatus.CANCELLED, TradeStatus.FAILED));
        // Terminal statuses — no further transitions
        map.put(TradeStatus.SETTLED, EnumSet.noneOf(TradeStatus.class));
        map.put(TradeStatus.CANCELLED, EnumSet.noneOf(TradeStatus.class));
        map.put(TradeStatus.FAILED, EnumSet.noneOf(TradeStatus.class));
        map.put(TradeStatus.AMENDED, EnumSet.noneOf(TradeStatus.class));
        PERMITTED = Collections.unmodifiableMap(map);

        EnumMap<TradeEventType, TradeStatus> eventMap = new EnumMap<>(TradeEventType.class);
        eventMap.put(TradeEventType.TRADE_CAPTURED, TradeStatus.CAPTURED);
        eventMap.put(TradeEventType.TRADE_VALIDATED, TradeStatus.VALIDATED);
        eventMap.put(TradeEventType.TRADE_ENRICHED, TradeStatus.ENRICHED);
        eventMap.put(TradeEventType.RISK_CALCULATION_COMPLETED, TradeStatus.RISK_CALCULATED);
        eventMap.put(TradeEventType.TRADE_BOOKED, TradeStatus.BOOKED);
        eventMap.put(TradeEventType.TRADE_ALLOCATED, TradeStatus.ALLOCATED);
        eventMap.put(TradeEventType.TRADE_CONFIRMED, TradeStatus.CONFIRMED);
        eventMap.put(TradeEventType.TRADE_SETTLED, TradeStatus.SETTLED);
        eventMap.put(TradeEventType.TRADE_CANCELLED, TradeStatus.CANCELLED);
        eventMap.put(TradeEventType.TRADE_AMENDED, TradeStatus.AMENDED);
        eventMap.put(TradeEventType.TRADE_FAILED, TradeStatus.FAILED);
        EVENT_TO_STATUS = Collections.unmodifiableMap(eventMap);
    }

    private StateMachine() {
        // utility class
    }

    /**
     * Returns true if transitioning from {@code from} to {@code to} is permitted.
     */
    public static boolean canTransition(TradeStatus from, TradeStatus to) {
        Set<TradeStatus> allowed = PERMITTED.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Returns the TradeStatus induced by the given event type, if mapped.
     */
    public static Optional<TradeStatus> targetFor(TradeEventType eventType) {
        return Optional.ofNullable(EVENT_TO_STATUS.get(eventType));
    }

    /**
     * Returns true if the given status is terminal (no further transitions allowed).
     */
    public static boolean isTerminal(TradeStatus status) {
        return TERMINAL.contains(status);
    }
}
