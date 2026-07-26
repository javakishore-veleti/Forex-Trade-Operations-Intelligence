package com.fxtradeops.reconciliation.domain.canonical;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.trade.TradeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CanonicalStateDeriver:
 * - Full path derives correctly
 * - Missing event → INCOMPLETE_HISTORY
 * - Same history ×2 → identical state (determinism)
 * - No majority vote / no LLM path
 */
class CanonicalStateDeriverTest {

    private CanonicalStateDeriver deriver;

    @BeforeEach
    void setUp() {
        deriver = new CanonicalStateDeriver();
    }

    @Test
    @DisplayName("Full happy path derives SETTLED")
    void fullPathDerivesSettled() {
        List<TradeEvent> history = List.of(
                event(TradeEventType.TRADE_CAPTURED, 1),
                event(TradeEventType.TRADE_VALIDATED, 2),
                event(TradeEventType.TRADE_ENRICHED, 3),
                event(TradeEventType.RISK_CALCULATION_COMPLETED, 4),
                event(TradeEventType.TRADE_BOOKED, 5),
                event(TradeEventType.TRADE_ALLOCATED, 6),
                event(TradeEventType.TRADE_CONFIRMED, 7),
                event(TradeEventType.TRADE_SETTLED, 8)
        );

        DerivationResult result = deriver.derive(history);

        assertEquals(TradeStatus.SETTLED, result.state());
        assertEquals(DerivationResult.DerivationStatus.COMPLETE, result.status());
    }

    @Test
    @DisplayName("Partial path derives BOOKED with COMPLETE status")
    void partialPathDerivesBooked() {
        List<TradeEvent> history = List.of(
                event(TradeEventType.TRADE_CAPTURED, 1),
                event(TradeEventType.TRADE_VALIDATED, 2),
                event(TradeEventType.TRADE_ENRICHED, 3),
                event(TradeEventType.RISK_CALCULATION_COMPLETED, 4),
                event(TradeEventType.TRADE_BOOKED, 5)
        );

        DerivationResult result = deriver.derive(history);

        assertEquals(TradeStatus.BOOKED, result.state());
        assertEquals(DerivationResult.DerivationStatus.COMPLETE, result.status());
    }

    @Test
    @DisplayName("Cancellation from VALIDATED derives CANCELLED")
    void cancellationPath() {
        List<TradeEvent> history = List.of(
                event(TradeEventType.TRADE_CAPTURED, 1),
                event(TradeEventType.TRADE_VALIDATED, 2),
                event(TradeEventType.TRADE_CANCELLED, 3)
        );

        DerivationResult result = deriver.derive(history);

        assertEquals(TradeStatus.CANCELLED, result.state());
        assertEquals(DerivationResult.DerivationStatus.COMPLETE, result.status());
    }

    @Test
    @DisplayName("Missing required event → INCOMPLETE_HISTORY at furthest state")
    void missingEventResultsInIncompleteHistory() {
        // Skip VALIDATED, go straight to ENRICHED — not permitted from CAPTURED
        List<TradeEvent> history = List.of(
                event(TradeEventType.TRADE_CAPTURED, 1),
                event(TradeEventType.TRADE_ENRICHED, 2)
        );

        DerivationResult result = deriver.derive(history);

        assertEquals(TradeStatus.CAPTURED, result.state());
        assertEquals(DerivationResult.DerivationStatus.INCOMPLETE_HISTORY, result.status());
    }

    @Test
    @DisplayName("Empty history → INCOMPLETE_HISTORY with null state")
    void emptyHistoryReturnsIncomplete() {
        DerivationResult result = deriver.derive(List.of());

        assertNull(result.state());
        assertEquals(DerivationResult.DerivationStatus.INCOMPLETE_HISTORY, result.status());
    }

    @Test
    @DisplayName("Null history → INCOMPLETE_HISTORY with null state")
    void nullHistoryReturnsIncomplete() {
        DerivationResult result = deriver.derive(null);

        assertNull(result.state());
        assertEquals(DerivationResult.DerivationStatus.INCOMPLETE_HISTORY, result.status());
    }

    @Test
    @DisplayName("Same history ×2 → identical state (determinism guarantee)")
    void determinismGuarantee() {
        List<TradeEvent> history = List.of(
                event(TradeEventType.TRADE_CAPTURED, 1),
                event(TradeEventType.TRADE_VALIDATED, 2),
                event(TradeEventType.TRADE_ENRICHED, 3),
                event(TradeEventType.RISK_CALCULATION_COMPLETED, 4),
                event(TradeEventType.TRADE_CANCELLED, 5)
        );

        DerivationResult result1 = deriver.derive(history);
        DerivationResult result2 = deriver.derive(history);

        assertEquals(result1.state(), result2.state());
        assertEquals(result1.status(), result2.status());
    }

    @Test
    @DisplayName("Non-status-changing events are skipped")
    void nonStatusChangingEventsSkipped() {
        List<TradeEvent> history = List.of(
                event(TradeEventType.TRADE_CAPTURED, 1),
                event(TradeEventType.RISK_CALCULATION_REQUESTED, 2), // non-status-changing
                event(TradeEventType.TRADE_VALIDATED, 3),
                event(TradeEventType.PROCESSING_PAUSED, 4), // non-status-changing
                event(TradeEventType.PROCESSING_RESUMED, 5) // non-status-changing
        );

        DerivationResult result = deriver.derive(history);

        assertEquals(TradeStatus.VALIDATED, result.state());
        assertEquals(DerivationResult.DerivationStatus.COMPLETE, result.status());
    }

    @Test
    @DisplayName("History not starting with CAPTURED → INCOMPLETE_HISTORY")
    void historyWithoutCapturedFirst() {
        List<TradeEvent> history = List.of(
                event(TradeEventType.TRADE_VALIDATED, 1)
        );

        DerivationResult result = deriver.derive(history);

        assertNull(result.state());
        assertEquals(DerivationResult.DerivationStatus.INCOMPLETE_HISTORY, result.status());
    }

    private TradeEvent event(TradeEventType type, long sequence) {
        return new TradeEvent(
                "evt-" + sequence,
                "FX-000001",
                "corr-001",
                type,
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(sequence),
                sequence,
                "test-service",
                Map.of()
        );
    }
}
