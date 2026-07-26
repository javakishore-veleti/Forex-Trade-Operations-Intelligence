package com.fxtradeops.reconciliation.domain.divergence;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flags a Divergence for every available source whose observed status differs from canonical.
 * UNAVAILABLE sources are excluded from divergence detection.
 */
@Component
public class DivergenceDetector {

    private final DivergenceClassifier classifier;

    public DivergenceDetector(DivergenceClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * Detects divergences between observed states and the canonical expected state.
     *
     * @param states    map of source → observed state
     * @param canonical the canonical expected state (may be null if derivation failed)
     * @return list of divergences (empty if all agree or canonical is null)
     */
    public List<Divergence> detect(Map<SourceId, ObservedState> states, TradeStatus canonical) {
        if (canonical == null) {
            return List.of();
        }

        List<Divergence> divergences = new ArrayList<>();

        for (ObservedState observed : states.values()) {
            if (!observed.available()) {
                continue; // UNAVAILABLE sources excluded
            }
            if (observed.status() == null) {
                continue;
            }
            if (observed.status() != canonical) {
                DivergenceClassification classification = classifier.classify(observed.status(), canonical);
                divergences.add(new Divergence(
                        observed.source(),
                        observed.status(),
                        canonical,
                        classification
                ));
            }
        }

        return divergences;
    }
}
