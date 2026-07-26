package com.fxtradeops.riskcalc.application;

import com.fxtradeops.riskcalc.persistence.LimitBreachEntity;
import com.fxtradeops.riskcalc.persistence.LimitEntity;
import com.fxtradeops.riskcalc.persistence.RiskAggregationEntity;
import com.fxtradeops.riskcalc.persistence.RiskResultEntity;
import com.fxtradeops.riskcalc.persistence.repositories.LimitBreachRepository;
import com.fxtradeops.riskcalc.persistence.repositories.LimitRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskAggregationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates risk results and aggregations against configured limits.
 * Records LimitBreach facts but NEVER blocks or cancels trades.
 */
@Service
public class LimitCheckerService {

    private static final Logger log = LoggerFactory.getLogger(LimitCheckerService.class);

    private final LimitRepository limitRepository;
    private final LimitBreachRepository limitBreachRepository;
    private final RiskAggregationRepository aggregationRepository;

    public LimitCheckerService(LimitRepository limitRepository,
                               LimitBreachRepository limitBreachRepository,
                               RiskAggregationRepository aggregationRepository) {
        this.limitRepository = limitRepository;
        this.limitBreachRepository = limitBreachRepository;
        this.aggregationRepository = aggregationRepository;
    }

    /**
     * Check a result against all applicable limits and record breach facts.
     */
    public void check(RiskResultEntity result) {
        // Check region limits
        checkScopeLimit(result.getCalculationId(), "REGION", result.getRegionCode());
        // Check book limits
        checkScopeLimit(result.getCalculationId(), "BOOK", result.getTradingBookId());
        // Check global limits
        checkScopeLimit(result.getCalculationId(), "GLOBAL", "GLOBAL");
    }

    private void checkScopeLimit(String calculationId, String scopeType, String scopeId) {
        List<LimitEntity> limits = limitRepository.findByScopeTypeAndScopeId(scopeType, scopeId);
        if (limits.isEmpty()) {
            return;
        }

        Optional<RiskAggregationEntity> aggregation = aggregationRepository
                .findByScopeTypeAndScopeId(scopeType, scopeId);

        if (aggregation.isEmpty()) {
            return;
        }

        BigDecimal observedAmount = aggregation.get().getTotalRiskAmount();

        for (LimitEntity limit : limits) {
            if (observedAmount.compareTo(limit.getLimitAmount()) > 0) {
                LimitBreachEntity breach = new LimitBreachEntity();
                breach.setBreachId(UUID.randomUUID().toString());
                breach.setCalculationId(calculationId);
                breach.setScopeType(scopeType);
                breach.setScopeId(scopeId);
                breach.setLimitAmount(limit.getLimitAmount());
                breach.setObservedAmount(observedAmount);
                breach.setDetectedAt(Instant.now());

                limitBreachRepository.save(breach);
                log.warn("Limit breach detected: scope={}/{}, limit={}, observed={}",
                        scopeType, scopeId, limit.getLimitAmount(), observedAmount);
            }
        }
    }
}
