package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.ContributingFactor;
import com.fxtradeops.domain.risk.RiskLevel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/**
 * Core deterministic risk engine. Creates a StatelessKieSession per calculation,
 * inserts the RiskFact, fires all rules, then decomposes factors and classifies level.
 */
@Component
public class RiskEngine {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.0001");

    private final KieContainer kieContainer;
    private final RiskLevelClassifier riskLevelClassifier;
    private final ContributingFactorCalculator contributingFactorCalculator;
    private final FallbackRuleDetector fallbackRuleDetector;
    private final String ruleVersion;
    private final MeterRegistry meterRegistry;

    public RiskEngine(KieContainer kieContainer,
                      RiskLevelClassifier riskLevelClassifier,
                      ContributingFactorCalculator contributingFactorCalculator,
                      FallbackRuleDetector fallbackRuleDetector,
                      String ruleVersion,
                      MeterRegistry meterRegistry) {
        this.kieContainer = kieContainer;
        this.riskLevelClassifier = riskLevelClassifier;
        this.contributingFactorCalculator = contributingFactorCalculator;
        this.fallbackRuleDetector = fallbackRuleDetector;
        this.ruleVersion = ruleVersion;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Compute risk for a given trade fact. Deterministic: same input → same output always.
     */
    public RiskComputationResult compute(RiskFact fact) {
        Timer.Sample sample = Timer.start(meterRegistry);

        // New StatelessKieSession per calculation — thread safe, no shared state
        StatelessKieSession session = kieContainer.newStatelessKieSession();
        session.execute(Collections.singletonList(fact));

        // Scale the computed risk amount
        BigDecimal riskAmount = fact.getRiskAmount().setScale(SCALE, ROUNDING);

        // Decompose into contributing factors
        List<ContributingFactor> factors = contributingFactorCalculator.decompose(fact, riskAmount);

        // Verify factor sum within tolerance
        BigDecimal factorSum = factors.stream()
                .map(ContributingFactor::contributionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (factorSum.subtract(riskAmount).abs().compareTo(ROUNDING_TOLERANCE) > 0) {
            throw new RiskArithmeticException(
                    "Contributing factor sum mismatch: factorSum=" + factorSum + ", riskAmount=" + riskAmount);
        }

        // Classify level
        RiskLevel level = riskLevelClassifier.classify(riskAmount, fact.getRegionCode());

        // Record metrics
        sample.stop(Timer.builder("risk_calculation_duration_seconds")
                .register(meterRegistry));

        Counter.builder("risk_calculations_total")
                .tag("region", fact.getRegionCode() != null ? fact.getRegionCode() : "UNKNOWN")
                .tag("risk_level", level.name())
                .register(meterRegistry)
                .increment();

        if (fallbackRuleDetector.isFallbackFired(fact.getRulesFired())) {
            Counter.builder("fallback_rule_firings_total")
                    .tag("pair", fact.getCurrencyPairCode() != null ? fact.getCurrencyPairCode() : "UNKNOWN")
                    .register(meterRegistry)
                    .increment();
        }

        return new RiskComputationResult(riskAmount, level, factors, fact.getRulesFired(), ruleVersion);
    }
}
