package com.fxtradeops.reconciliation.domain.divergence;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
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
 * Unit tests for StaleSourceResolver — deterministic most-likely-stale selection.
 */
class StaleSourceResolverTest {

    private StaleSourceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StaleSourceResolver();
    }

    @Test
    @DisplayName("Earliest timestamp wins")
    void earliestTimestampWins() {
        Instant early = Instant.parse("2024-01-01T00:00:00Z");
        Instant late = Instant.parse("2024-01-01T01:00:00Z");

        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.VALIDATED, late, true));
        states.put(SourceId.CACHE, new ObservedState(SourceId.CACHE, TradeStatus.CAPTURED, early, true));

        List<Divergence> divergences = List.of(
                new Divergence(SourceId.RELATIONAL, TradeStatus.VALIDATED, TradeStatus.BOOKED, DivergenceClassification.STALE),
                new Divergence(SourceId.CACHE, TradeStatus.CAPTURED, TradeStatus.BOOKED, DivergenceClassification.STALE)
        );

        assertEquals(SourceId.CACHE, resolver.resolve(divergences, states));
    }

    @Test
    @DisplayName("Same timestamp — fixed source precedence breaks tie")
    void fixedPrecedenceBreaksTie() {
        Instant same = Instant.parse("2024-01-01T00:00:00Z");

        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.VALIDATED, same, true));
        states.put(SourceId.CACHE, new ObservedState(SourceId.CACHE, TradeStatus.CAPTURED, same, true));

        List<Divergence> divergences = List.of(
                new Divergence(SourceId.RELATIONAL, TradeStatus.VALIDATED, TradeStatus.BOOKED, DivergenceClassification.STALE),
                new Divergence(SourceId.CACHE, TradeStatus.CAPTURED, TradeStatus.BOOKED, DivergenceClassification.STALE)
        );

        // CACHE has lower precedence value (0) → more likely stale
        assertEquals(SourceId.CACHE, resolver.resolve(divergences, states));
    }

    @Test
    @DisplayName("Empty divergences → null")
    void emptyDivergencesReturnsNull() {
        assertNull(resolver.resolve(List.of(), Map.of()));
    }

    @Test
    @DisplayName("Null divergences → null")
    void nullDivergencesReturnsNull() {
        assertNull(resolver.resolve(null, Map.of()));
    }
}
