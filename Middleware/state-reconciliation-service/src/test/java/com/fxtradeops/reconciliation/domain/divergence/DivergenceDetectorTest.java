package com.fxtradeops.reconciliation.domain.divergence;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DivergenceDetector.
 */
class DivergenceDetectorTest {

    private DivergenceDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DivergenceDetector(new DivergenceClassifier());
    }

    @Test
    @DisplayName("All sources agree with canonical → zero divergences (CONSISTENT)")
    void allSourcesAgree() {
        Instant now = Instant.now();
        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.BOOKED, now, true));
        states.put(SourceId.DOCUMENT, new ObservedState(SourceId.DOCUMENT, TradeStatus.BOOKED, now, true));
        states.put(SourceId.CACHE, new ObservedState(SourceId.CACHE, TradeStatus.BOOKED, now, true));
        states.put(SourceId.EVENT_STREAM, new ObservedState(SourceId.EVENT_STREAM, TradeStatus.BOOKED, now, true));

        List<Divergence> result = detector.detect(states, TradeStatus.BOOKED);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("UNAVAILABLE sources are excluded from divergence detection")
    void unavailableSourcesExcluded() {
        Instant now = Instant.now();
        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.BOOKED, now, true));
        states.put(SourceId.DOCUMENT, ObservedState.unavailable(SourceId.DOCUMENT));
        states.put(SourceId.CACHE, new ObservedState(SourceId.CACHE, TradeStatus.BOOKED, now, true));

        List<Divergence> result = detector.detect(states, TradeStatus.BOOKED);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Source with different status → flagged as divergence")
    void divergentSource() {
        Instant now = Instant.now();
        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.BOOKED, now, true));
        states.put(SourceId.CACHE, new ObservedState(SourceId.CACHE, TradeStatus.VALIDATED, now, true));

        List<Divergence> result = detector.detect(states, TradeStatus.BOOKED);

        assertEquals(1, result.size());
        assertEquals(SourceId.CACHE, result.get(0).source());
    }

    @Test
    @DisplayName("Null canonical → no divergences")
    void nullCanonicalNoDivergences() {
        Instant now = Instant.now();
        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.BOOKED, now, true));

        List<Divergence> result = detector.detect(states, null);

        assertTrue(result.isEmpty());
    }
}
