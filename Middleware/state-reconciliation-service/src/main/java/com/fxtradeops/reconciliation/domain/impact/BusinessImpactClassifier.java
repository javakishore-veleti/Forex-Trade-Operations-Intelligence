package com.fxtradeops.reconciliation.domain.impact;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Deterministic business-impact classification from divergence nature and canonical stage.
 * No monetary computation — references risk/exposure context where needed.
 */
@Component
public class BusinessImpactClassifier {

    /**
     * Late-stage statuses where divergences have higher business impact.
     */
    private static final Set<TradeStatus> LATE_STAGE = Set.of(
            TradeStatus.CONFIRMED, TradeStatus.SETTLED, TradeStatus.CANCELLED
    );

    /**
     * Classifies business impact deterministically.
     *
     * @param derivation the derivation result (for canonical state)
     * @param divergences detected divergences
     * @return business impact ordinal
     */
    public BusinessImpact classify(DerivationResult derivation, List<Divergence> divergences) {
        if (divergences == null || divergences.isEmpty()) {
            return BusinessImpact.NONE;
        }

        boolean settlementStage = derivation.state() != null && isLateStage(derivation.state());
        boolean conflicting = divergences.stream()
                .anyMatch(d -> d.classification() == DivergenceClassification.CONFLICTING);

        if (settlementStage && conflicting) {
            return BusinessImpact.CRITICAL;
        }
        if (conflicting) {
            return BusinessImpact.HIGH;
        }
        if (settlementStage) {
            return BusinessImpact.MEDIUM;
        }
        return BusinessImpact.LOW;
    }

    private boolean isLateStage(TradeStatus status) {
        return LATE_STAGE.contains(status);
    }
}
