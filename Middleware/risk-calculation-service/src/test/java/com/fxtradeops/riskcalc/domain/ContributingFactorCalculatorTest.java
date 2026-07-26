package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.ContributingFactor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContributingFactorCalculator.
 */
class ContributingFactorCalculatorTest {

    private final ContributingFactorCalculator calculator = new ContributingFactorCalculator();

    @Test
    void factorsSumToRiskAmount() {
        RiskFact fact = new RiskFact("FX-000001", "EURUSD", "EUR", "USD",
                new BigDecimal("1000000"), "USD", "EMEA", "FX-BOOK-001");
        fact.setVolatilityFactor(new BigDecimal("25000.0000"));
        fact.setNotionalExposureFactor(new BigDecimal("10000.0000"));
        fact.setRegionalAdjustmentFactor(new BigDecimal("5000.0000"));

        BigDecimal riskAmount = new BigDecimal("40000.0000");
        List<ContributingFactor> factors = calculator.decompose(fact, riskAmount);

        assertEquals(3, factors.size());
        assertEquals("CURRENCY_PAIR_VOLATILITY", factors.get(0).factorName());
        assertEquals("NOTIONAL_EXPOSURE", factors.get(1).factorName());
        assertEquals("REGIONAL_ADJUSTMENT", factors.get(2).factorName());

        BigDecimal sum = factors.stream()
                .map(ContributingFactor::contributionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, riskAmount.compareTo(sum),
                "Factor sum should equal risk amount");
    }

    @Test
    void alwaysEmitsThreeFactors() {
        RiskFact fact = new RiskFact("FX-000002", "UNKNOWN", "AAA", "BBB",
                new BigDecimal("500000"), "USD", "APAC", "FX-BOOK-002");
        fact.setVolatilityFactor(new BigDecimal("10000.0000"));
        fact.setNotionalExposureFactor(new BigDecimal("5000.0000"));
        fact.setRegionalAdjustmentFactor(new BigDecimal("2500.0000"));

        BigDecimal riskAmount = new BigDecimal("17500.0000");
        List<ContributingFactor> factors = calculator.decompose(fact, riskAmount);

        assertEquals(3, factors.size());
    }

    @Test
    void allFactorsHaveCurrency() {
        RiskFact fact = new RiskFact("FX-000003", "GBPUSD", "GBP", "USD",
                new BigDecimal("2000000"), "GBP", "EMEA", "FX-BOOK-003");
        fact.setVolatilityFactor(new BigDecimal("56000.0000"));
        fact.setNotionalExposureFactor(new BigDecimal("22000.0000"));
        fact.setRegionalAdjustmentFactor(new BigDecimal("12000.0000"));

        BigDecimal riskAmount = new BigDecimal("90000.0000");
        List<ContributingFactor> factors = calculator.decompose(fact, riskAmount);

        for (ContributingFactor factor : factors) {
            assertEquals("GBP", factor.currency());
        }
    }
}
