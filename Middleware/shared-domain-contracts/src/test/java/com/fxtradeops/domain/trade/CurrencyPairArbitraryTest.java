package com.fxtradeops.domain.trade;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import net.jqwik.api.*;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.StringLength;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyPairArbitraryTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Property
    void threeCharUppercaseAccepted(
            @ForAll @StringLength(3) @CharRange(from = 'A', to = 'Z') String base,
            @ForAll @StringLength(3) @CharRange(from = 'A', to = 'Z') String quote) {
        CurrencyPair pair = new CurrencyPair(base, quote, base + "/" + quote);
        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertTrue(violations.isEmpty(),
                "Expected no violations for valid 3-char currencies: " + base + "/" + quote);
    }

    @Property
    void twoCharStringRejected(
            @ForAll @StringLength(2) @CharRange(from = 'A', to = 'Z') String twoChar) {
        CurrencyPair pair = new CurrencyPair(twoChar, "USD", twoChar + "/USD");
        Set<ConstraintViolation<CurrencyPair>> violations = validator.validate(pair);
        assertFalse(violations.isEmpty(),
                "Expected violations for 2-char currency: " + twoChar);
    }
}
