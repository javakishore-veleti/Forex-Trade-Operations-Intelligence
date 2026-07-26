package com.fxtradeops.reconciliation.domain.canonical;

import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.trade.TradeStatus;

import java.util.Map;
import java.util.Set;

/**
 * Immutable mirror of the lifecycle state machine transition table.
 * Deterministic — shared/duplicated from trade-lifecycle-service.
 * This is the authoritative reference for canonical-state derivation.
 */
public final class LifecycleTransitions {

    private LifecycleTransitions() {
        // utility class
    }

    /**
     * The permitted transitions from each non-terminal state.
     * Terminal states (SETTLED, CANCELLED, FAILED) have no outgoing transitions.
     */
    public static final Map<TradeStatus, Set<TradeStatus>> PERMITTED = Map.of(
            TradeStatus.CAPTURED, Set.of(TradeStatus.VALIDATED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED),
            TradeStatus.VALIDATED, Set.of(TradeStatus.ENRICHED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED),
            TradeStatus.ENRICHED, Set.of(TradeStatus.RISK_CALCULATED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED),
            TradeStatus.RISK_CALCULATED, Set.of(TradeStatus.BOOKED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED),
            TradeStatus.BOOKED, Set.of(TradeStatus.ALLOCATED, TradeStatus.CANCELLED, TradeStatus.AMENDED, TradeStatus.FAILED),
            TradeStatus.ALLOCATED, Set.of(TradeStatus.CONFIRMED, TradeStatus.CANCELLED, TradeStatus.FAILED),
            TradeStatus.CONFIRMED, Set.of(TradeStatus.SETTLED, TradeStatus.CANCELLED, TradeStatus.FAILED)
    );

    /**
     * Terminal statuses — no further transitions are possible.
     */
    public static final Set<TradeStatus> TERMINAL = Set.of(
            TradeStatus.SETTLED, TradeStatus.CANCELLED, TradeStatus.FAILED
    );

    /**
     * Maps event type to the target trade status it induces.
     * Returns null for non-status-changing events.
     */
    public static TradeStatus targetFor(TradeEventType eventType) {
        return switch (eventType) {
            case TRADE_CAPTURED -> TradeStatus.CAPTURED;
            case TRADE_VALIDATED -> TradeStatus.VALIDATED;
            case TRADE_ENRICHED -> TradeStatus.ENRICHED;
            case RISK_CALCULATION_COMPLETED -> TradeStatus.RISK_CALCULATED;
            case TRADE_BOOKED -> TradeStatus.BOOKED;
            case TRADE_ALLOCATED -> TradeStatus.ALLOCATED;
            case TRADE_CONFIRMED -> TradeStatus.CONFIRMED;
            case TRADE_SETTLED -> TradeStatus.SETTLED;
            case TRADE_CANCELLED -> TradeStatus.CANCELLED;
            case TRADE_AMENDED -> TradeStatus.AMENDED;
            case TRADE_FAILED -> TradeStatus.FAILED;
            default -> null; // non-status-changing events (REQUEST, REPLAYED, PAUSED, RESUMED)
        };
    }

    /**
     * The canonical forward path (happy path) ordering.
     * Used to determine STALE vs AHEAD classification.
     */
    public static final TradeStatus[] FORWARD_PATH = {
            TradeStatus.CAPTURED,
            TradeStatus.VALIDATED,
            TradeStatus.ENRICHED,
            TradeStatus.RISK_CALCULATED,
            TradeStatus.BOOKED,
            TradeStatus.ALLOCATED,
            TradeStatus.CONFIRMED,
            TradeStatus.SETTLED
    };

    /**
     * Returns the index of a status on the forward path, or -1 if not on it.
     */
    public static int pathIndex(TradeStatus status) {
        for (int i = 0; i < FORWARD_PATH.length; i++) {
            if (FORWARD_PATH[i] == status) return i;
        }
        return -1;
    }

    /**
     * Checks whether a given status is reachable on the canonical path leading to the canonical state.
     */
    public static boolean onCanonicalPath(TradeStatus observed, TradeStatus canonical) {
        int ic = pathIndex(canonical);
        int io = pathIndex(observed);
        // If canonical is a terminal off the forward path (CANCELLED, FAILED),
        // any forward-path state before the transition is "on path" (STALE)
        if (canonical == TradeStatus.CANCELLED || canonical == TradeStatus.FAILED) {
            return io >= 0; // any forward-path state is a predecessor
        }
        if (canonical == TradeStatus.AMENDED) {
            return io >= 0; // AMENDED branches off any state
        }
        // Both on forward path
        if (io >= 0 && ic >= 0) {
            return true; // both on path - position determines STALE/AHEAD
        }
        return false;
    }
}
