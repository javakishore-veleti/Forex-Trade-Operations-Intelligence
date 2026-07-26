package com.fxtradeops.riskcalc.domain;

import com.fxtradeops.domain.risk.ContributingFactor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Decomposes a risk computation into contributing factors.
 * Always emits CURRENCY_PAIR_VOLATILITY, NOTIONAL_EXPOSURE, REGIONAL_ADJUSTMENT.
 */
@Component
public class ContributingFactorCalculator {

    /**
     * Decompose a RiskFact's computed amounts into named contributing factors.
     * The factors always sum to riskAmount (enforced by the caller within tolerance).
     */
    public List<ContributingFactor> decompose(RiskFact fact, BigDecimal riskAmount) {
        List<ContributingFactor> factors = new ArrayList<>();
        String currency = fact.getNotionalCurrency();

        BigDecimal volatility = fact.getVolatilityFactor().setScale(4, RoundingMode.HALF_UP);
        BigDecimal notionalExposure = fact.getNotionalExposureFactor().setScale(4, RoundingMode.HALF_UP);

        // Regional adjustment is the remainder to ensure exact sum
        BigDecimal regionalAdjustment = riskAmount
                .subtract(volatility)
                .subtract(notionalExposure)
                .setScale(4, RoundingMode.HALF_UP);

        factors.add(new ContributingFactor("CURRENCY_PAIR_VOLATILITY", volatility, currency));
        factors.add(new ContributingFactor("NOTIONAL_EXPOSURE", notionalExposure, currency));
        factors.add(new ContributingFactor("REGIONAL_ADJUSTMENT", regionalAdjustment, currency));

        return factors;
    }
}
