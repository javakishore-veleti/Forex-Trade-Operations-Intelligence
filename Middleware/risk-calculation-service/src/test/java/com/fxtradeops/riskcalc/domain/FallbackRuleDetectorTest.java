package com.fxtradeops.riskcalc.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FallbackRuleDetector.
 */
class FallbackRuleDetectorTest {

    private final FallbackRuleDetector detector = new FallbackRuleDetector();

    @Test
    void detectsFallbackRule() {
        assertTrue(detector.isFallbackFired(List.of("FALLBACK")));
        assertTrue(detector.isFallbackFired(List.of("FX-PAIR-001", "FALLBACK")));
    }

    @Test
    void noFallbackWhenSpecificRulesFired() {
        assertFalse(detector.isFallbackFired(List.of("FX-PAIR-EURUSD-001")));
        assertFalse(detector.isFallbackFired(List.of("FX-PAIR-USDJPY-002", "FX-REGION-APAC-042")));
    }

    @Test
    void handleNullAndEmpty() {
        assertFalse(detector.isFallbackFired(null));
        assertFalse(detector.isFallbackFired(List.of()));
    }
}
