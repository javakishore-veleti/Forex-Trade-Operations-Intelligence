package com.fxtradeops.domain.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyPairTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private CurrencyPair validPair() {
        return new CurrencyPair("USD", "INR", "USD/INR");
    }

    @Test
    void validConstruction() {
        CurrencyPair pair = validPair();
        assertEquals("USD", pair.baseCurrency());
        assertEquals("INR", pair.quoteCurrency());
        assertEquals("USD/INR", pair.pairCode());

        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankBaseCurrencyRejected() {
        CurrencyPair pair = new CurrencyPair("", "INR", "USD/INR");
        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertFalse(violations.isEmpty());
    }

    @Test
    void blankQuoteCurrencyRejected() {
        CurrencyPair pair = new CurrencyPair("USD", "", "USD/INR");
        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertFalse(violations.isEmpty());
    }

    @Test
    void blankPairCodeRejected() {
        CurrencyPair pair = new CurrencyPair("USD", "INR", "");
        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertFalse(violations.isEmpty());
    }

    @Test
    void baseCurrencyTwoCharsRejected() {
        CurrencyPair pair = new CurrencyPair("US", "INR", "US/INR");
        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertFalse(violations.isEmpty());
    }

    @Test
    void baseCurrencyFourCharsRejected() {
        CurrencyPair pair = new CurrencyPair("USDD", "INR", "USDD/INR");
        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        CurrencyPair original = validPair();
        String json = mapper.writeValueAsString(original);
        CurrencyPair deserialized = mapper.readValue(json, CurrencyPair.class);
        assertEquals(original, deserialized);
    }
}
