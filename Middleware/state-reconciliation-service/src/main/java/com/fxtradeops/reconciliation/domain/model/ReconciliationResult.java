package com.fxtradeops.reconciliation.domain.model;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.action.PermittedAction;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.impact.BusinessImpact;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The full reconciliation result envelope — PRD-compatible.
 * Contains observed states, canonical expected state, divergences,
 * violated invariants, permitted actions, and business impact.
 */
public record ReconciliationResult(
        String tradeId,
        Map<SourceId, ObservedState> states,
        TradeStatus expectedState,
        DerivationResult.DerivationStatus derivation,
        List<Divergence> divergences,
        SourceId mostLikelyStaleSource,
        List<ViolatedInvariant> violatedInvariants,
        Set<PermittedAction> permittedActions,
        BusinessImpact businessImpact
) {
}
