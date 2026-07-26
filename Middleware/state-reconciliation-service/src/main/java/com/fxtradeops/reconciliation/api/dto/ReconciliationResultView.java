package com.fxtradeops.reconciliation.api.dto;

import com.fxtradeops.reconciliation.domain.action.PermittedAction;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.impact.BusinessImpact;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.ReconciliationResult;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.domain.model.ViolatedInvariant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PRD-compatible JSON response envelope for reconciliation results.
 */
public record ReconciliationResultView(
        String tradeId,
        Map<String, ObservedStateView> states,
        String expectedState,
        String derivation,
        List<DivergenceView> divergences,
        String mostLikelyStaleSource,
        List<ViolatedInvariantView> violatedInvariants,
        Set<String> permittedActions,
        String businessImpact
) {

    public static ReconciliationResultView from(ReconciliationResult result) {
        Map<String, ObservedStateView> stateViews = new LinkedHashMap<>();
        for (Map.Entry<SourceId, ObservedState> entry : result.states().entrySet()) {
            stateViews.put(entry.getKey().name(), ObservedStateView.from(entry.getValue()));
        }

        List<DivergenceView> divergenceViews = result.divergences().stream()
                .map(DivergenceView::from)
                .toList();

        List<ViolatedInvariantView> invariantViews = result.violatedInvariants().stream()
                .map(vi -> new ViolatedInvariantView(vi.code(), vi.description()))
                .toList();

        Set<String> actionNames = result.permittedActions().stream()
                .map(PermittedAction::name)
                .collect(Collectors.toSet());

        return new ReconciliationResultView(
                result.tradeId(),
                stateViews,
                result.expectedState() != null ? result.expectedState().name() : null,
                result.derivation() != null ? result.derivation().name() : null,
                divergenceViews,
                result.mostLikelyStaleSource() != null ? result.mostLikelyStaleSource().name() : null,
                invariantViews,
                actionNames,
                result.businessImpact() != null ? result.businessImpact().name() : null
        );
    }
}
