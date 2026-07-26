package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.ContributingFactor;
import com.fxtradeops.domain.risk.RiskLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RiskEngine: determinism, factor sum, fallback detection.
 * Uses FX- prefixed synthetic IDs and fictional rule names.
 */
class RiskEngineTest {

    private static KieContainer kieContainer;
    private static RiskEngine riskEngine;

    @BeforeAll
    static void setUp() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        InputStream drlStream = RiskEngineTest.class.getResourceAsStream("/rules/currency-pair-risk.drl");
        kfs.write("src/main/resources/rules/currency-pair-risk.drl",
                ResourceFactory.newInputStreamResource(drlStream));
        KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll();
        assertTrue(kieBuilder.getResults().getMessages(org.kie.api.builder.Message.Level.ERROR).isEmpty(),
                "DRL should compile without errors");
        kieContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());

        riskEngine = new RiskEngine(
                kieContainer,
                new RiskLevelClassifier(
                        new BigDecimal("50000.0000"),
                        new BigDecimal("200000.0000"),
                        new BigDecimal("500000.0000")
                ),
                new ContributingFactorCalculator(),
                new FallbackRuleDetector(),
                "RULES-7.14",
                new SimpleMeterRegistry()
        );
    }

    @Test
    void determinism_sameInputProducesSameOutput() {
        RiskFact fact1 = createFact("FX-000001", "EURUSD", "EUR", "USD",
                new BigDecimal("1000000"), "USD", "EMEA", "FX-BOOK-001");
        RiskFact fact2 = createFact("FX-000001", "EURUSD", "EUR", "USD",
                new BigDecimal("1000000"), "USD", "EMEA", "FX-BOOK-001");

        RiskComputationResult result1 = riskEngine.compute(fact1);
        RiskComputationResult result2 = riskEngine.compute(fact2);

        assertEquals(result1.riskAmount(), result2.riskAmount());
        assertEquals(result1.riskLevel(), result2.riskLevel());
        assertEquals(result1.rulesFired(), result2.rulesFired());
    }

    @Test
    void factorsSumToRiskAmount() {
        RiskFact fact = createFact("FX-000002", "EURUSD", "EUR", "USD",
                new BigDecimal("2000000"), "USD", "EMEA", "FX-BOOK-002");

        RiskComputationResult result = riskEngine.compute(fact);

        BigDecimal factorSum = result.factors().stream()
                .map(ContributingFactor::contributionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(factorSum.subtract(result.riskAmount()).abs()
                .compareTo(new BigDecimal("0.0001")) <= 0,
                "Factor sum should equal risk amount within tolerance");
    }

    @Test
    void fallbackFiresOnUncoveredPair() {
        RiskFact fact = createFact("FX-000003", "NZDJPY", "NZD", "JPY",
                new BigDecimal("500000"), "NZD", "APAC", "FX-BOOK-003");

        RiskComputationResult result = riskEngine.compute(fact);

        assertTrue(result.rulesFired().contains("FALLBACK"),
                "Fallback rule should fire for uncovered pair");
        assertFalse(result.riskAmount().compareTo(BigDecimal.ZERO) == 0,
                "Risk amount should not be zero");
    }

    @Test
    void specificPairRuleFires() {
        RiskFact fact = createFact("FX-000004", "USDJPY", "USD", "JPY",
                new BigDecimal("1000000"), "USD", "APAC", "FX-BOOK-004");

        RiskComputationResult result = riskEngine.compute(fact);

        assertTrue(result.rulesFired().contains("FX-PAIR-USDJPY-002"),
                "USD/JPY rule should fire");
    }

    @Test
    void riskLevelClassified() {
        RiskFact fact = createFact("FX-000005", "EURUSD", "EUR", "USD",
                new BigDecimal("1000000"), "USD", "EMEA", "FX-BOOK-005");

        RiskComputationResult result = riskEngine.compute(fact);

        // EUR/USD: 0.025 + 0.01 + 0.005 = 0.04 × 1M = 40000 → LOW
        assertEquals(RiskLevel.LOW, result.riskLevel());
    }

    @Test
    void ruleVersionIsSet() {
        RiskFact fact = createFact("FX-000006", "EURUSD", "EUR", "USD",
                new BigDecimal("1000000"), "USD", "EMEA", "FX-BOOK-006");

        RiskComputationResult result = riskEngine.compute(fact);

        assertEquals("RULES-7.14", result.ruleVersion());
    }

    private RiskFact createFact(String tradeId, String pairCode, String base, String quote,
                                BigDecimal notional, String currency, String region, String book) {
        return new RiskFact(tradeId, pairCode, base, quote, notional, currency, region, book);
    }
}
