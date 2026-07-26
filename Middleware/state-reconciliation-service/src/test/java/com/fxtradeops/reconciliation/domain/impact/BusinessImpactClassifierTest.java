package com.fxtradeops.reconciliation.domain.impact;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for BusinessImpactClassifier.
 */
class BusinessImpactClassifierTest {

    private BusinessImpactClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new BusinessImpactClassifier();
    }

    @Test
    @DisplayName("Settlement-stage conflict → CRITICAL")
    void settlementStageConflictIsCritical() {
        DerivationResult derivation = DerivationResult.complete(TradeStatus.CONFIRMED);
        List<Divergence> divs = List.of(
                new Divergence(SourceId.RELATIONAL, TradeStatus.CANCELLED, TradeStatus.CONFIRMED, DivergenceClassification.CONFLICTING)
        );

        assertEquals(BusinessImpact.CRITICAL, classifier.classify(derivation, divs));
    }

    @Test
    @DisplayName("Early-stage conflict → HIGH")
    void earlyStageConflictIsHigh() {
        DerivationResult derivation = DerivationResult.complete(TradeStatus.BOOKED);
        List<Divergence> divs = List.of(
                new Divergence(SourceId.RELATIONAL, TradeStatus.CANCELLED, TradeStatus.BOOKED, DivergenceClassification.CONFLICTING)
        );

        assertEquals(BusinessImpact.HIGH, classifier.classify(derivation, divs));
    }

    @Test
    @DisplayName("Settlement-stage stale (no conflict) → MEDIUM")
    void settlementStageStaleisMedium() {
        DerivationResult derivation = DerivationResult.complete(TradeStatus.SETTLED);
        List<Divergence> divs = List.of(
                new Divergence(SourceId.CACHE, TradeStatus.CONFIRMED, TradeStatus.SETTLED, DivergenceClassification.STALE)
        );

        assertEquals(BusinessImpact.MEDIUM, classifier.classify(derivation, divs));
    }

    @Test
    @DisplayName("Early-stage cache lag → LOW")
    void earlyStageCacheLagIsLow() {
        DerivationResult derivation = DerivationResult.complete(TradeStatus.ENRICHED);
        List<Divergence> divs = List.of(
                new Divergence(SourceId.CACHE, TradeStatus.CAPTURED, TradeStatus.ENRICHED, DivergenceClassification.STALE)
        );

        assertEquals(BusinessImpact.LOW, classifier.classify(derivation, divs));
    }

    @Test
    @DisplayName("Consistent (no divergences) → NONE")
    void consistentIsNone() {
        DerivationResult derivation = DerivationResult.complete(TradeStatus.BOOKED);

        assertEquals(BusinessImpact.NONE, classifier.classify(derivation, List.of()));
    }

    @Test
    @DisplayName("Null divergences → NONE")
    void nullDivergencesIsNone() {
        DerivationResult derivation = DerivationResult.complete(TradeStatus.BOOKED);

        assertEquals(BusinessImpact.NONE, classifier.classify(derivation, null));
    }
}
