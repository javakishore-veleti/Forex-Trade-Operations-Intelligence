package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.RiskLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for RiskLevelClassifier — deterministic threshold classification.
 */
class RiskLevelClassifierTest {

    private final RiskLevelClassifier classifier = new RiskLevelClassifier(
            new BigDecimal("50000.0000"),
            new BigDecimal("200000.0000"),
            new BigDecimal("500000.0000")
    );

    @Test
    void classify_lowRisk() {
        assertEquals(RiskLevel.LOW, classifier.classify(new BigDecimal("10000.0000"), "EMEA"));
        assertEquals(RiskLevel.LOW, classifier.classify(new BigDecimal("50000.0000"), "APAC"));
    }

    @Test
    void classify_mediumRisk() {
        assertEquals(RiskLevel.MEDIUM, classifier.classify(new BigDecimal("50000.0001"), "EMEA"));
        assertEquals(RiskLevel.MEDIUM, classifier.classify(new BigDecimal("200000.0000"), "APAC"));
    }

    @Test
    void classify_highRisk() {
        assertEquals(RiskLevel.HIGH, classifier.classify(new BigDecimal("200000.0001"), "EMEA"));
        assertEquals(RiskLevel.HIGH, classifier.classify(new BigDecimal("500000.0000"), "APAC"));
    }

    @Test
    void classify_criticalRisk() {
        assertEquals(RiskLevel.CRITICAL, classifier.classify(new BigDecimal("500000.0001"), "EMEA"));
        assertEquals(RiskLevel.CRITICAL, classifier.classify(new BigDecimal("1000000.0000"), "APAC"));
    }

    @Test
    void classify_deterministic_sameInputSameOutput() {
        BigDecimal amount = new BigDecimal("75000.0000");
        RiskLevel first = classifier.classify(amount, "APAC");
        RiskLevel second = classifier.classify(amount, "APAC");
        assertEquals(first, second);
    }
}
