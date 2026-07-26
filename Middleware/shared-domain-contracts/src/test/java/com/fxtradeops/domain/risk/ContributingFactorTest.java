package com.fxtradeops.domain.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContributingFactorTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private ContributingFactor validFactor() {
        return new ContributingFactor("MarketVolatility", new BigDecimal("5000.1234"), "USD");
    }

    @Test
    void validConstruction() {
        ContributingFactor factor = validFactor();
        assertEquals("MarketVolatility", factor.factorName());
        assertEquals(new BigDecimal("5000.1234"), factor.contributionAmount());
        assertEquals("USD", factor.currency());

        Set<ConstraintViolation<ContributingFactor>> violations = validator.validate(factor);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankFactorNameRejected() {
        ContributingFactor factor = new ContributingFactor("", new BigDecimal("5000.1234"), "USD");
        Set<ConstraintViolation<ContributingFactor>> violations = validator.validate(factor);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullContributionAmountRejected() {
        ContributingFactor factor = new ContributingFactor("MarketVolatility", null, "USD");
        Set<ConstraintViolation<ContributingFactor>> violations = validator.validate(factor);
        assertFalse(violations.isEmpty());
    }

    @Test
    void negativeContributionAmountRejected() {
        ContributingFactor factor = new ContributingFactor("MarketVolatility", new BigDecimal("-100"), "USD");
        Set<ConstraintViolation<ContributingFactor>> violations = validator.validate(factor);
        assertFalse(violations.isEmpty());
    }

    @Test
    void invalidCurrencySizeRejected() {
        ContributingFactor factor = new ContributingFactor("MarketVolatility", new BigDecimal("5000.1234"), "US");
        Set<ConstraintViolation<ContributingFactor>> violations = validator.validate(factor);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        ContributingFactor original = validFactor();
        String json = mapper.writeValueAsString(original);
        ContributingFactor deserialized = mapper.readValue(json, ContributingFactor.class);
        assertEquals(original, deserialized);
    }
}
