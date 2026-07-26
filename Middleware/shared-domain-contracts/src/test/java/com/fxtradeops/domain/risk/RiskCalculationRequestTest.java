package com.fxtradeops.domain.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import com.fxtradeops.domain.reference.RegionCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RiskCalculationRequestTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private RiskCalculationRequest validRequest() {
        return new RiskCalculationRequest(
                "FX-000001",
                "CORR-000001",
                "REQ-000001",
                RegionCode.EMEA,
                "BOOK-EMEA-001",
                Instant.parse("2025-06-15T09:30:00Z"),
                5
        );
    }

    @Test
    void validConstruction() {
        RiskCalculationRequest request = validRequest();
        assertEquals("FX-000001", request.tradeId());
        assertEquals("CORR-000001", request.correlationId());
        assertEquals("REQ-000001", request.requestId());
        assertEquals(RegionCode.EMEA, request.regionCode());
        assertEquals("BOOK-EMEA-001", request.tradingBookId());
        assertEquals(Instant.parse("2025-06-15T09:30:00Z"), request.requestedAt());
        assertEquals(5, request.priority());

        Set<ConstraintViolation<RiskCalculationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullTradeIdRejected() {
        RiskCalculationRequest request = new RiskCalculationRequest(
                null, "CORR-000001", "REQ-000001", RegionCode.EMEA,
                "BOOK-EMEA-001", Instant.now(), 5);
        Set<ConstraintViolation<RiskCalculationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullRegionCodeRejected() {
        RiskCalculationRequest request = new RiskCalculationRequest(
                "FX-000001", "CORR-000001", "REQ-000001", null,
                "BOOK-EMEA-001", Instant.now(), 5);
        Set<ConstraintViolation<RiskCalculationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void zeroPriorityRejected() {
        RiskCalculationRequest request = new RiskCalculationRequest(
                "FX-000001", "CORR-000001", "REQ-000001", RegionCode.EMEA,
                "BOOK-EMEA-001", Instant.now(), 0);
        Set<ConstraintViolation<RiskCalculationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        RiskCalculationRequest original = validRequest();
        String json = mapper.writeValueAsString(original);
        RiskCalculationRequest deserialized = mapper.readValue(json, RiskCalculationRequest.class);
        assertEquals(original, deserialized);
    }
}
