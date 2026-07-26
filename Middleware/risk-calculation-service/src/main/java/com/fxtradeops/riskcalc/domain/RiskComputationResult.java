package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.ContributingFactor;
import com.fxtradeops.domain.risk.RiskLevel;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable result of a risk computation — value type produced by the risk engine.
 */
public record RiskComputationResult(
        BigDecimal riskAmount,
        RiskLevel riskLevel,
        List<ContributingFactor> factors,
        List<String> rulesFired,
        String ruleVersion
) {
    public RiskComputationResult {
        factors = List.copyOf(factors);
        rulesFired = List.copyOf(rulesFired);
    }
}
