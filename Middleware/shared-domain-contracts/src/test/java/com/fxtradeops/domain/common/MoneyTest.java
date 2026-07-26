package com.fxtradeops.domain.common;

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

class MoneyTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    @Test
    void validConstruction() {
        Money money = new Money(new BigDecimal("100.50"), "USD");
        assertEquals(new BigDecimal("100.50"), money.amount());
        assertEquals("USD", money.currency());

        Set<ConstraintViolation<Money>> violations = validator.validate(money);
        assertTrue(violations.isEmpty());
    }

    @Test
    void scaleEnforcement() {
        Money money = new Money(new BigDecimal("99.999"), "USD");
        assertEquals(2, money.amount().scale());
        assertEquals(new BigDecimal("100.00"), money.amount());
    }

    @Test
    void scaleEnforcementLessDecimals() {
        Money money = new Money(new BigDecimal("100"), "USD");
        assertEquals(2, money.amount().scale());
        assertEquals(new BigDecimal("100.00"), money.amount());
    }

    @Test
    void negativeAmountRejected() {
        Money money = new Money(new BigDecimal("-10.00"), "USD");
        Set<ConstraintViolation<Money>> violations = validator.validate(money);
        assertFalse(violations.isEmpty());
    }

    @Test
    void zeroAmountRejected() {
        Money money = new Money(new BigDecimal("0.00"), "USD");
        Set<ConstraintViolation<Money>> violations = validator.validate(money);
        assertFalse(violations.isEmpty());
    }

    @Test
    void blankCurrencyRejected() {
        Money money = new Money(new BigDecimal("100.00"), "");
        Set<ConstraintViolation<Money>> violations = validator.validate(money);
        assertFalse(violations.isEmpty());
    }

    @Test
    void invalidCurrencySizeRejected() {
        Money money = new Money(new BigDecimal("100.00"), "US");
        Set<ConstraintViolation<Money>> violations = validator.validate(money);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullAmountThrowsNPE() {
        assertThrows(NullPointerException.class, () -> new Money(null, "USD"));
    }

    @Test
    void serializationRoundTrip() throws Exception {
        Money original = new Money(new BigDecimal("12345.67"), "EUR");
        String json = mapper.writeValueAsString(original);
        Money deserialized = mapper.readValue(json, Money.class);
        assertEquals(original, deserialized);
    }
}
