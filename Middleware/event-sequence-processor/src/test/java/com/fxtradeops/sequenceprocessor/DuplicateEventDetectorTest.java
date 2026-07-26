package com.fxtradeops.sequenceprocessor;

import com.fxtradeops.domain.event.TradeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateEventDetectorTest {

    private DuplicateEventDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DuplicateEventDetector();
    }

    @Test
    void shouldDetectDuplicate_whenEventIdAlreadySeen() {
        SequenceFact fact = new SequenceFact(
                "FX-000001",
                List.of(new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now())),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_CAPTURED, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detect(
                fact, "evt-1", TradeEventType.TRADE_CAPTURED, "corr-1", "hash-a", "hash-a");

        assertTrue(result.isPresent());
        assertEquals(ViolationType.DUPLICATE_EVENT, result.get().violationType());
    }

    @Test
    void shouldDetectConflictingReplay_whenEventIdSeenWithDifferentPayload() {
        SequenceFact fact = new SequenceFact(
                "FX-000002",
                List.of(new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now())),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_CAPTURED, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detect(
                fact, "evt-1", TradeEventType.TRADE_CAPTURED, "corr-2", "hash-b", "hash-a");

        assertTrue(result.isPresent());
        assertEquals(ViolationType.CONFLICTING_REPLAY, result.get().violationType());
    }

    @Test
    void shouldNotDetectDuplicate_whenEventIdNotSeen() {
        SequenceFact fact = new SequenceFact(
                "FX-000003",
                List.of(new SequenceFact.ObservedEvent("evt-1", TradeEventType.TRADE_CAPTURED, Instant.now())),
                List.of(), List.of(), List.of(), List.of(),
                TradeEventType.TRADE_CAPTURED, Instant.now()
        );

        Optional<AnomalyEnvelope> result = detector.detect(
                fact, "evt-2", TradeEventType.TRADE_VALIDATED, "corr-3", "hash-c", "hash-c");

        assertFalse(result.isPresent());
    }
}
