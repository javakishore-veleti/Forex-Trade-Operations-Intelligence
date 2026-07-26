package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.ContributingFactor;
import com.fxtradeops.domain.risk.RiskLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for domain enums and value types.
 */
class DomainValueTypesTest {

    @Test
    void riskLevel_hasAllValues() {
        assertEquals(4, RiskLevel.values().length);
        assertNotNull(RiskLevel.valueOf("LOW"));
        assertNotNull(RiskLevel.valueOf("MEDIUM"));
        assertNotNull(RiskLevel.valueOf("HIGH"));
        assertNotNull(RiskLevel.valueOf("CRITICAL"));
    }

    @Test
    void scopeType_hasAllValues() {
        assertEquals(4, ScopeType.values().length);
        assertNotNull(ScopeType.valueOf("REGION"));
        assertNotNull(ScopeType.valueOf("BOOK"));
        assertNotNull(ScopeType.valueOf("GLOBAL"));
        assertNotNull(ScopeType.valueOf("COUNTERPARTY"));
    }

    @Test
    void contributingFactor_immutability() {
        ContributingFactor factor = new ContributingFactor(
                "CURRENCY_PAIR_VOLATILITY",
                new BigDecimal("1000.0000"),
                "USD"
        );
        assertEquals("CURRENCY_PAIR_VOLATILITY", factor.factorName());
        assertEquals(new BigDecimal("1000.0000"), factor.contributionAmount());
        assertEquals("USD", factor.currency());
    }

    @Test
    void riskComputationResult_immutability() {
        var factors = java.util.List.of(
                new ContributingFactor("CURRENCY_PAIR_VOLATILITY", new BigDecimal("500.0000"), "USD"),
                new ContributingFactor("NOTIONAL_EXPOSURE", new BigDecimal("300.0000"), "USD"),
                new ContributingFactor("REGIONAL_ADJUSTMENT", new BigDecimal("200.0000"), "USD")
        );
        var result = new RiskComputationResult(
                new BigDecimal("1000.0000"),
                RiskLevel.LOW,
                factors,
                java.util.List.of("FX-PAIR-EURUSD-001"),
                "RULES-7.14"
        );
        assertEquals(new BigDecimal("1000.0000"), result.riskAmount());
        assertEquals(RiskLevel.LOW, result.riskLevel());
        assertEquals(3, result.factors().size());
        assertEquals(1, result.rulesFired().size());
        assertEquals("RULES-7.14", result.ruleVersion());
    }
}
