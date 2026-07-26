package com.fxtradeops.domain.common;

import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyArbitraryTest {

    @Property
    void amountAlwaysHasScaleTwo(@ForAll @BigRange(min = "0.01", max = "999999999.99") BigDecimal amount) {
        Money money = new Money(amount, "USD");
        assertEquals(2, money.amount().scale(),
                "Money amount scale must always be 2, but was " + money.amount().scale() + " for input " + amount);
    }
}
