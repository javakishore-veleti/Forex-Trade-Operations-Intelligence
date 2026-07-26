package com.fxtradeops.domain.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import com.fxtradeops.domain.reference.RegionCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RiskResultTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private RiskResult validResult() {
        return new RiskResult(
                "FX-000001",
                "CALC-000001",
                new BigDecimal("25000.5000"),
                "USD",
                RegionCode.APAC,
                "BOOK-APAC-001",
                Instant.parse("2025-06-15T10:00:00Z"),
                "v1.2.0",
                RiskLevel.MEDIUM
        );
    }

    @Test
    void validConstruction() {
        RiskResult result = validResult();
        assertEquals("FX-000001", result.tradeId());
        assertEquals("CALC-000001", result.calculationId());
        assertEquals(new BigDecimal("25000.5000"), result.riskAmount());
        assertEquals("USD", result.riskCurrency());
        assertEquals(RegionCode.APAC, result.regionCode());
        assertEquals("BOOK-APAC-001", result.tradingBookId());
        assertEquals(Instant.parse("2025-06-15T10:00:00Z"), result.calculatedAt());
        assertEquals("v1.2.0", result.ruleVersion());
        assertEquals(RiskLevel.MEDIUM, result.riskLevel());

        Set<ConstraintViolation<RiskResult>> violations = validator.validate(result);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullTradeIdRejected() {
        RiskResult result = new RiskResult(
                null, "CALC-000001", new BigDecimal("25000.5000"), "USD",
                RegionCode.APAC, "BOOK-APAC-001", Instant.now(), "v1.2.0", RiskLevel.MEDIUM);
        Set<ConstraintViolation<RiskResult>> violations = validator.validate(result);
        assertFalse(violations.isEmpty());
    }

    @Test
    void negativeRiskAmountRejected() {
        RiskResult result = new RiskResult(
                "FX-000001", "CALC-000001", new BigDecimal("-100"), "USD",
                RegionCode.APAC, "BOOK-APAC-001", Instant.now(), "v1.2.0", RiskLevel.MEDIUM);
        Set<ConstraintViolation<RiskResult>> violations = validator.validate(result);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullRiskLevelRejected() {
        RiskResult result = new RiskResult(
                "FX-000001", "CALC-000001", new BigDecimal("25000.5000"), "USD",
                RegionCode.APAC, "BOOK-APAC-001", Instant.now(), "v1.2.0", null);
        Set<ConstraintViolation<RiskResult>> violations = validator.validate(result);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        RiskResult original = validResult();
        String json = mapper.writeValueAsString(original);
        RiskResult deserialized = mapper.readValue(json, RiskResult.class);
        assertEquals(original, deserialized);
    }
}
