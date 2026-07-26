package com.fxtradeops.riskcalc.domain;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects whether the "FALLBACK" rule fired during a Drools evaluation.
 */
@Component
public class FallbackRuleDetector {

    public static final String FALLBACK_RULE_NAME = "FALLBACK";

    /**
     * Returns true if the FALLBACK rule is present in the rules fired list.
     */
    public boolean isFallbackFired(List<String> rulesFired) {
        return rulesFired != null && rulesFired.stream()
                .anyMatch(rule -> rule.contains(FALLBACK_RULE_NAME));
    }
}
