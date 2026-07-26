package com.fxtradeops.domain.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CounterpartyTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private Counterparty validCounterparty() {
        return new Counterparty(
                "CP-AURORA-001", "Aurelia Capital Markets",
                CounterpartyType.BANK, RegionCode.EMEA, true, "AA+");
    }

    @Test
    void validConstruction() {
        Counterparty cp = validCounterparty();
        assertEquals("CP-AURORA-001", cp.counterpartyId());
        assertEquals("Aurelia Capital Markets", cp.counterpartyName());
        assertEquals(CounterpartyType.BANK, cp.counterpartyType());
        assertEquals(RegionCode.EMEA, cp.regionCode());
        assertTrue(cp.isActive());
        assertEquals("AA+", cp.creditRating());

        Set<ConstraintViolation<Counterparty>> violations = validator.validate(cp);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankCounterpartyIdRejected() {
        Counterparty cp = new Counterparty(
                "", "Aurelia Capital Markets",
                CounterpartyType.BANK, RegionCode.EMEA, true, "AA+");
        Set<ConstraintViolation<Counterparty>> violations = validator.validate(cp);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullCounterpartyTypeRejected() {
        Counterparty cp = new Counterparty(
                "CP-AURORA-001", "Aurelia Capital Markets",
                null, RegionCode.EMEA, true, "AA+");
        Set<ConstraintViolation<Counterparty>> violations = validator.validate(cp);
        assertFalse(violations.isEmpty());
    }

    @Test
    void blankCreditRatingRejected() {
        Counterparty cp = new Counterparty(
                "CP-AURORA-001", "Aurelia Capital Markets",
                CounterpartyType.BANK, RegionCode.EMEA, true, "");
        Set<ConstraintViolation<Counterparty>> violations = validator.validate(cp);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        Counterparty original = validCounterparty();
        String json = mapper.writeValueAsString(original);
        Counterparty deserialized = mapper.readValue(json, Counterparty.class);
        assertEquals(original, deserialized);
    }
}
