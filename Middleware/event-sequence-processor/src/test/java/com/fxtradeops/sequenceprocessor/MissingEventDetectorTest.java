package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MissingEventDetectorTest {

    private MissingEventDetector detector;

    @BeforeEach
    void setUp() {
        detector = new MissingEventDetector();
    }

    @Test
    void shouldDetectMissingEvent_whenPredecessorNotObserved() {
        SequenceFact fact = new SequenceFact(
                "FX-000001",
                List.of(new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now())),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_CAPTURED, Instant.now()
        );

        // TRADE_ENRICHED arrives without TRADE_VALIDATED
        Optional<AnomalyEnvelope> result = detector.detect(fact, TradeEventType.TRADE_ENRICHED, "corr-1");

        assertTrue(result.isPresent());
        assertEquals(ViolationType.MISSING_EVENT, result.get().violationType());
        assertEquals("FX-000001", result.get().tradeId());
    }

    @Test
    void shouldNotDetectMissing_whenPredecessorIsObserved() {
        SequenceFact fact = new SequenceFact(
                "FX-000002",
                List.of(
                        new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now()),
                        new SequenceFact.ObservedEvent("evt-2", TradeEventType.TRADE_VALIDATED, Instant.now())
                ),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_VALIDATED, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detect(fact, TradeEventType.TRADE_ENRICHED, "corr-2");

        assertFalse(result.isPresent());
    }

    @Test
    void shouldNotDetectMissing_forFirstEvent() {
        SequenceFact fact = new SequenceFact(
                "FX-000003",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detect(fact, TradeEventType.TRADE_CAPTURED, "corr-3");

        assertFalse(result.isPresent());
    }

    @Test
    void shouldDetectResolution_whenMissingEventArrives() {
        SequenceFact fact = new SequenceFact(
                "FX-000004",
                List.of(new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now())),
                List.of(),
                List.of(TradeEventType.TRADE_VALIDATED), // was missing
                List.of(), List.of(),
                TradeEventType.TRADE_CAPTURED, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detectResolution(fact, TradeEventType.TRADE_VALIDATED, "corr-4");

        assertTrue(result.isPresent());
        assertEquals(ViolationType.MISSING_EVENT_RESOLVED, result.get().violationType());
    }

    @Test
    void shouldNotDetectResolution_whenEventWasNotMissing() {
        SequenceFact fact = new SequenceFact(
                "FX-000005",
                List.of(new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now())),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_CAPTURED, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detectResolution(fact, TradeEventType.TRADE_VALIDATED, "corr-5");

        assertFalse(result.isPresent());
    }
}
