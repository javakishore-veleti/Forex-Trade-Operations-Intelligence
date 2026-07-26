package com.fxtradeops.reconciliation.domain.divergence;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for DivergenceClassifier — STALE, AHEAD, CONFLICTING classification.
 */
class DivergenceClassifierTest {

    private DivergenceClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new DivergenceClassifier();
    }

    @Test
    @DisplayName("Observed earlier on forward path → STALE")
    void observedBehindCanonical() {
        assertEquals(DivergenceClassification.STALE,
                classifier.classify(TradeStatus.VALIDATED, TradeStatus.BOOKED));
    }

    @Test
    @DisplayName("Observed later on forward path → AHEAD")
    void observedAheadOfCanonical() {
        assertEquals(DivergenceClassification.AHEAD,
                classifier.classify(TradeStatus.CONFIRMED, TradeStatus.BOOKED));
    }

    @Test
    @DisplayName("Observed CANCELLED when canonical is on forward path → CONFLICTING")
    void observedOffPathWhenCanonicalOnPath() {
        assertEquals(DivergenceClassification.CONFLICTING,
                classifier.classify(TradeStatus.CANCELLED, TradeStatus.BOOKED));
    }

    @Test
    @DisplayName("Observed on forward path when canonical is CANCELLED → STALE")
    void observedOnPathWhenCanonicalCancelled() {
        assertEquals(DivergenceClassification.STALE,
                classifier.classify(TradeStatus.BOOKED, TradeStatus.CANCELLED));
    }

    @Test
    @DisplayName("Observed FAILED when canonical is CANCELLED → CONFLICTING")
    void observedFailedWhenCanonicalCancelled() {
        assertEquals(DivergenceClassification.CONFLICTING,
                classifier.classify(TradeStatus.FAILED, TradeStatus.CANCELLED));
    }

    @Test
    @DisplayName("Observed CAPTURED when canonical is ENRICHED → STALE")
    void observedCapturedWhenCanonicalEnriched() {
        assertEquals(DivergenceClassification.STALE,
                classifier.classify(TradeStatus.CAPTURED, TradeStatus.ENRICHED));
    }

    @Test
    @DisplayName("Observed SETTLED when canonical is CONFIRMED → AHEAD")
    void observedSettledWhenCanonicalConfirmed() {
        assertEquals(DivergenceClassification.AHEAD,
                classifier.classify(TradeStatus.SETTLED, TradeStatus.CONFIRMED));
    }
}
