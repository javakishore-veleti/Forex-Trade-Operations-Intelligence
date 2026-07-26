package com.fxtradeops.riskcalc.application;

import com.fxtradeops.riskcalc.domain.ScopeType;
import com.fxtradeops.riskcalc.persistence.RiskAggregationEntity;
import com.fxtradeops.riskcalc.persistence.RiskResultEntity;
import com.fxtradeops.riskcalc.persistence.repositories.RiskAggregationRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskResultRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Maintains running risk aggregations by region, trading book, and globally.
 */
@Service
public class AggregationService {

    private final RiskAggregationRepository aggregationRepository;
    private final RiskResultRepository resultRepository;

    public AggregationService(RiskAggregationRepository aggregationRepository,
                              RiskResultRepository resultRepository) {
        this.aggregationRepository = aggregationRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Apply a new risk result to REGION, BOOK, and GLOBAL aggregations.
     */
    public void apply(RiskResultEntity result) {
        updateAggregation(ScopeType.REGION.name(), result.getRegionCode(),
                result.getRiskAmount(), result.getRiskCurrency(), 1);
        updateAggregation(ScopeType.BOOK.name(), result.getTradingBookId(),
                result.getRiskAmount(), result.getRiskCurrency(), 1);
        updateAggregation(ScopeType.GLOBAL.name(), "GLOBAL",
                result.getRiskAmount(), result.getRiskCurrency(), 1);
    }

    /**
     * Reverse a trade's contribution from all affected aggregations.
     * Used when a trade is cancelled or superseded by amendment.
     */
    public void reverseContribution(String tradeId) {
        Optional<RiskResultEntity> latestResult = resultRepository
                .findFirstByTradeIdOrderByCalculatedAtDesc(tradeId);

        latestResult.ifPresent(result -> {
            BigDecimal negatedAmount = result.getRiskAmount().negate();
            updateAggregation(ScopeType.REGION.name(), result.getRegionCode(),
                    negatedAmount, result.getRiskCurrency(), -1);
            updateAggregation(ScopeType.BOOK.name(), result.getTradingBookId(),
                    negatedAmount, result.getRiskCurrency(), -1);
            updateAggregation(ScopeType.GLOBAL.name(), "GLOBAL",
                    negatedAmount, result.getRiskCurrency(), -1);
        });
    }

    private void updateAggregation(String scopeType, String scopeId,
                                   BigDecimal amount, String currency, int tradeCountDelta) {
        RiskAggregationEntity aggregation = aggregationRepository
                .findByScopeTypeAndScopeId(scopeType, scopeId)
                .orElseGet(() -> {
                    RiskAggregationEntity newAgg = new RiskAggregationEntity();
                    newAgg.setScopeType(scopeType);
                    newAgg.setScopeId(scopeId);
                    newAgg.setTotalRiskAmount(BigDecimal.ZERO);
                    newAgg.setRiskCurrency(currency);
                    newAgg.setTradeCount(0);
                    newAgg.setLastUpdatedAt(Instant.now());
                    return newAgg;
                });

        aggregation.setTotalRiskAmount(aggregation.getTotalRiskAmount().add(amount));
        aggregation.setTradeCount(aggregation.getTradeCount() + tradeCountDelta);
        aggregation.setLastUpdatedAt(Instant.now());
        aggregation.setRiskCurrency(currency);
        aggregationRepository.save(aggregation);
    }
}
