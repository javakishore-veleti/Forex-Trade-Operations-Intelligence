package com.fxtradeops.reconciliation.domain.canonical;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.trade.TradeStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Derives the canonical expected state deterministically from ordered event history.
 * Pure function — no majority vote, no LLM, no side effects.
 * Same transition table as trade-lifecycle-service, duplicated as immutable mirror.
 */
@Component
public class CanonicalStateDeriver {

    /**
     * Derives the canonical expected state by folding the ordered event history
     * through the lifecycle state machine.
     *
     * @param orderedHistory events ordered by (occurredAt, sequenceNumber)
     * @return DerivationResult with COMPLETE or INCOMPLETE_HISTORY status
     */
    public DerivationResult derive(List<TradeEvent> orderedHistory) {
        if (orderedHistory == null || orderedHistory.isEmpty()) {
            return DerivationResult.incomplete(null);
        }

        TradeStatus state = null;

        for (TradeEvent event : orderedHistory) {
            TradeStatus target = LifecycleTransitions.targetFor(event.eventType());

            // Skip non-status-changing events
            if (target == null) {
                continue;
            }

            // First status-changing event must be CAPTURED
            if (state == null) {
                if (target == TradeStatus.CAPTURED) {
                    state = TradeStatus.CAPTURED;
                    continue;
                }
                // History doesn't start with CAPTURED — incomplete
                return DerivationResult.incomplete(null);
            }

            // Terminal states allow no further transitions
            if (LifecycleTransitions.TERMINAL.contains(state)) {
                // Event after terminal state — incomplete (unexpected)
                return DerivationResult.incomplete(state);
            }

            // Check if transition is permitted
            Set<TradeStatus> permitted = LifecycleTransitions.PERMITTED.get(state);
            if (permitted != null && permitted.contains(target)) {
                state = target;
                continue;
            }

            // Transition not permitted from current state — incomplete history (gap)
            return DerivationResult.incomplete(state);
        }

        if (state == null) {
            return DerivationResult.incomplete(null);
        }

        return DerivationResult.complete(state);
    }
}
