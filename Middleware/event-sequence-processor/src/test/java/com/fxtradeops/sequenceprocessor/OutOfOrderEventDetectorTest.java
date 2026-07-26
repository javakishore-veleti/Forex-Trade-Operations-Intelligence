package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OutOfOrderEventDetectorTest {

    private OutOfOrderEventDetector detector;

    @BeforeEach
    void setUp() {
        detector = new OutOfOrderEventDetector();
    }

    @Test
    void shouldDetectOutOfOrder_whenEventIsEarlierThanCurrentStatus() {
        SequenceFact fact = new SequenceFact(
                "FX-000001",
                List.of(
                        new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now()),
                        new SequenceFact.ObservedEvent("evt-2", TradeEventType.TRADE_VALIDATED, Instant.now()),
                        new SequenceFact.ObservedEvent("evt-3", TradeEventType.TRADE_ENRICHED, Instant.now())
                ),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_ENRICHED, Instant.now()
        );

        // TRADE_CAPTURED arrives again (ordinal 0 < current ordinal 2)
        Optional<AnomalyEnvelope> result = detector.detect(fact, TradeEventType.TRADE_CAPTURED, "corr-1");

        assertTrue(result.isPresent());
        assertEquals(ViolationType.OUT_OF_ORDER_EVENT, result.get().violationType());
        assertEquals("TRADE_CAPTURED", result.get().details().get("arrivingEventType"));
        assertEquals("TRADE_ENRICHED", result.get().details().get("currentFactStatus"));
    }

    @Test
    void shouldNotDetectOutOfOrder_whenEventIsNextInSequence() {
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
    void shouldNotDetectOutOfOrder_whenNoCurrentStatus() {
        SequenceFact fact = new SequenceFact(
                "FX-000003",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detect(fact, TradeEventType.TRADE_CAPTURED, "corr-3");

        assertFalse(result.isPresent());
    }

    @Test
    void shouldNotDetectOutOfOrder_forNonLifecycleEvents() {
        SequenceFact fact = new SequenceFact(
                "FX-000004",
                List.of(new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now())),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_ENRICHED, Instant.now()
        );

        // TRADE_AMENDED is not in lifecycle order (ordinal = -1)
        Optional<AnomalyEnvelope> result = detector.detect(fact, TradeEventType.TRADE_AMENDED, "corr-4");

        assertFalse(result.isPresent());
    }
}
