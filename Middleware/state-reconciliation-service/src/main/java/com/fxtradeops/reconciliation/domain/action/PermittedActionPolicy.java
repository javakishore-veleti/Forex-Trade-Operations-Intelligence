package com.fxtradeops.reconciliation.domain.action;

import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic mapping from divergences to permitted actions.
 * Catalogue-bounded — never free-form, never model-expanded, never executes.
 */
@Component
public class PermittedActionPolicy {

    /**
     * Determines the set of permitted corrective actions for the given divergences.
     * Pure function — no side effects, no execution.
     *
     * @param divergences the detected divergences
     * @param derivation  the derivation result
     * @return set of permitted actions from the fixed catalogue
     */
    public Set<PermittedAction> permit(List<Divergence> divergences, DerivationResult derivation) {
        EnumSet<PermittedAction> out = EnumSet.noneOf(PermittedAction.class);

        for (Divergence d : divergences) {
            if (d.source() == SourceId.CACHE && d.classification() == DivergenceClassification.STALE) {
                out.add(PermittedAction.REFRESH_CACHE);
            }
            if (d.source() == SourceId.EVENT_STREAM && d.classification() == DivergenceClassification.STALE) {
                out.add(PermittedAction.REPLAY_EVENT);
            }
            if (d.source() == SourceId.DOCUMENT && d.classification() == DivergenceClassification.STALE) {
                out.add(PermittedAction.RESYNC_DOCUMENT_STORE);
            }
            if (d.source() == SourceId.RELATIONAL && d.classification() == DivergenceClassification.STALE) {
                out.add(PermittedAction.RESYNC_RELATIONAL_STORE);
            }
            if (d.classification() == DivergenceClassification.CONFLICTING) {
                out.add(PermittedAction.OPEN_RECONCILIATION_CASE);
            }
        }

        if (out.isEmpty()) {
            out.add(PermittedAction.NO_ACTION);
        }

        return out;
    }
}
