package com.fxtradeops.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeEventTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private TradeEvent validEvent() {
        return new TradeEvent(
                "EVT-000001",
                "FX-000001",
                "CORR-000001",
                TradeEventType.TRADE_CAPTURED,
                Instant.parse("2025-06-15T09:00:00Z"),
                1L,
                "trade-ingest-service",
                Map.of("action", "capture", "amount", 1000000)
        );
    }

    @Test
    void validConstruction() {
        TradeEvent event = validEvent();
        assertEquals("EVT-000001", event.eventId());
        assertEquals("FX-000001", event.tradeId());
        assertEquals("CORR-000001", event.correlationId());
        assertEquals(TradeEventType.TRADE_CAPTURED, event.eventType());
        assertEquals(Instant.parse("2025-06-15T09:00:00Z"), event.occurredAt());
        assertEquals(1L, event.sequenceNumber());
        assertEquals("trade-ingest-service", event.sourceService());
        assertEquals(Map.of("action", "capture", "amount", 1000000), event.payload());

        Set<ConstraintViolation<TradeEvent>> violations = validator.validate(event);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullEventIdRejected() {
        TradeEvent event = new TradeEvent(
                null, "FX-000001", "CORR-000001", TradeEventType.TRADE_CAPTURED,
                Instant.now(), 1L, "trade-ingest-service", Map.of());
        Set<ConstraintViolation<TradeEvent>> violations = validator.validate(event);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullTradeIdRejected() {
        TradeEvent event = new TradeEvent(
                "EVT-000001", null, "CORR-000001", TradeEventType.TRADE_CAPTURED,
                Instant.now(), 1L, "trade-ingest-service", Map.of());
        Set<ConstraintViolation<TradeEvent>> violations = validator.validate(event);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullEventTypeRejected() {
        TradeEvent event = new TradeEvent(
                "EVT-000001", "FX-000001", "CORR-000001", null,
                Instant.now(), 1L, "trade-ingest-service", Map.of());
        Set<ConstraintViolation<TradeEvent>> violations = validator.validate(event);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullPayloadRejected() {
        TradeEvent event = new TradeEvent(
                "EVT-000001", "FX-000001", "CORR-000001", TradeEventType.TRADE_CAPTURED,
                Instant.now(), 1L, "trade-ingest-service", null);
        Set<ConstraintViolation<TradeEvent>> violations = validator.validate(event);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        TradeEvent original = validEvent();
        String json = mapper.writeValueAsString(original);
        TradeEvent deserialized = mapper.readValue(json, TradeEvent.class);
        assertEquals(original.eventId(), deserialized.eventId());
        assertEquals(original.tradeId(), deserialized.tradeId());
        assertEquals(original.correlationId(), deserialized.correlationId());
        assertEquals(original.eventType(), deserialized.eventType());
        assertEquals(original.occurredAt(), deserialized.occurredAt());
        assertEquals(original.sequenceNumber(), deserialized.sequenceNumber());
        assertEquals(original.sourceService(), deserialized.sourceService());
        // Map equality: keys and primitive values
        assertEquals(original.payload().get("action"), deserialized.payload().get("action"));
        assertEquals(original.payload().get("amount"), deserialized.payload().get("amount"));
    }
}
