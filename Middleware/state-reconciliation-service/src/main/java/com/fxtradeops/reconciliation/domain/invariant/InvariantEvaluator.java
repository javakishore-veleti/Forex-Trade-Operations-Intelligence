package com.fxtradeops.reconciliation.domain.invariant;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.canonical.LifecycleTransitions;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.domain.model.ViolatedInvariant;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates cross-source invariants against observed states, canonical state, and derivation result.
 * Returns all violated invariants with stable codes and descriptions.
 */
@Component
public class InvariantEvaluator {

    /**
     * Evaluates all invariants and returns violations found.
     */
    public List<ViolatedInvariant> evaluate(
            Map<SourceId, ObservedState> states,
            TradeStatus canonical,
            DerivationResult derivationResult,
            List<Divergence> divergences) {

        List<ViolatedInvariant> violations = new ArrayList<>();

        // INV_SETTLED_NOT_PENDING_IN_CACHE
        if (canonical == TradeStatus.SETTLED) {
            ObservedState cacheState = states.get(SourceId.CACHE);
            if (cacheState != null && cacheState.available() && cacheState.status() == TradeStatus.CAPTURED) {
                violations.add(toViolation(Invariant.INV_SETTLED_NOT_PENDING_IN_CACHE));
            }
        }

        // INV_CANCELLED_NOT_ADVANCING
        if (canonical == TradeStatus.CANCELLED) {
            for (ObservedState observed : states.values()) {
                if (!observed.available() || observed.status() == null) continue;
                // A post-cancel advancing status (forward-path beyond where cancel would occur)
                int observedIdx = LifecycleTransitions.pathIndex(observed.status());
                if (observed.status() != TradeStatus.CANCELLED &&
                        observed.status() != TradeStatus.FAILED &&
                        observedIdx >= 0) {
                    // Any forward-path status when canonical is CANCELLED means it hasn't
                    // reflected the cancellation - but specifically check for "advancing" states
                    // that are ahead of early stages (BOOKED, ALLOCATED, CONFIRMED, SETTLED)
                    if (observedIdx >= LifecycleTransitions.pathIndex(TradeStatus.BOOKED)) {
                        violations.add(toViolation(Invariant.INV_CANCELLED_NOT_ADVANCING));
                        break;
                    }
                }
            }
        }

        // INV_NO_SOURCE_AHEAD_OF_CANONICAL
        if (canonical != null && LifecycleTransitions.TERMINAL.contains(canonical)) {
            boolean hasAhead = divergences.stream()
                    .anyMatch(d -> d.classification() == DivergenceClassification.AHEAD);
            if (hasAhead) {
                violations.add(toViolation(Invariant.INV_NO_SOURCE_AHEAD_OF_CANONICAL));
            }
        }

        // INV_HISTORY_COMPLETE
        if (canonical != null && LifecycleTransitions.TERMINAL.contains(canonical)
                && derivationResult.status() == DerivationResult.DerivationStatus.INCOMPLETE_HISTORY) {
            violations.add(toViolation(Invariant.INV_HISTORY_COMPLETE));
        }

        return violations;
    }

    private ViolatedInvariant toViolation(Invariant invariant) {
        return new ViolatedInvariant(invariant.code(), invariant.description());
    }
}
