package com.fxtradeops.reconciliation.domain.divergence;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.canonical.LifecycleTransitions;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import org.springframework.stereotype.Component;

/**
 * Classifies a divergence as STALE, AHEAD, or CONFLICTING
 * based on the observed status's position relative to the canonical state on the lifecycle path.
 */
@Component
public class DivergenceClassifier {

    /**
     * Classifies the divergence between an observed status and the canonical status.
     *
     * @param observed  the status reported by a source
     * @param canonical the canonical expected status
     * @return STALE, AHEAD, or CONFLICTING
     */
    public DivergenceClassification classify(TradeStatus observed, TradeStatus canonical) {
        int io = LifecycleTransitions.pathIndex(observed);
        int ic = LifecycleTransitions.pathIndex(canonical);

        // If canonical is a terminal state off the forward path (CANCELLED, FAILED, AMENDED)
        if (canonical == TradeStatus.CANCELLED || canonical == TradeStatus.FAILED) {
            // If observed is on the forward path, it's behind the terminal (STALE if we haven't reached terminal)
            if (io >= 0) {
                return DivergenceClassification.STALE;
            }
            // If observed is also a terminal but different from canonical
            return DivergenceClassification.CONFLICTING;
        }

        if (canonical == TradeStatus.AMENDED) {
            if (io >= 0) {
                return DivergenceClassification.STALE;
            }
            return DivergenceClassification.CONFLICTING;
        }

        // Canonical is on the forward path
        if (io < 0) {
            // Observed is not on the forward path (e.g., CANCELLED, FAILED, AMENDED)
            // but canonical is — so observed is off the canonical path
            return DivergenceClassification.CONFLICTING;
        }

        // Both on forward path — compare positions
        if (io < ic) {
            return DivergenceClassification.STALE;
        }
        if (io > ic) {
            return DivergenceClassification.AHEAD;
        }

        // Equal positions shouldn't reach here (detector only calls for divergences)
        return DivergenceClassification.CONFLICTING;
    }
}
