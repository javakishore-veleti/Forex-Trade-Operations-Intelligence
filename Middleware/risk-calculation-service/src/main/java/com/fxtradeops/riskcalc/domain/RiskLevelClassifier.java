package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.RiskLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Deterministic, configurable threshold-based classifier for RiskLevel.
 * Pure function: same riskAmount and region → same RiskLevel, always.
 */
@Component
public class RiskLevelClassifier {

    private final BigDecimal lowMax;
    private final BigDecimal mediumMax;
    private final BigDecimal highMax;

    public RiskLevelClassifier(
            @Value("${risk.thresholds.low-max:50000.0000}") BigDecimal lowMax,
            @Value("${risk.thresholds.medium-max:200000.0000}") BigDecimal mediumMax,
            @Value("${risk.thresholds.high-max:500000.0000}") BigDecimal highMax) {
        this.lowMax = lowMax;
        this.mediumMax = mediumMax;
        this.highMax = highMax;
    }

    /**
     * Classify risk level deterministically from a risk amount.
     */
    public RiskLevel classify(BigDecimal riskAmount, String regionCode) {
        if (riskAmount.compareTo(lowMax) <= 0) {
            return RiskLevel.LOW;
        } else if (riskAmount.compareTo(mediumMax) <= 0) {
            return RiskLevel.MEDIUM;
        } else if (riskAmount.compareTo(highMax) <= 0) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.CRITICAL;
        }
    }
}
