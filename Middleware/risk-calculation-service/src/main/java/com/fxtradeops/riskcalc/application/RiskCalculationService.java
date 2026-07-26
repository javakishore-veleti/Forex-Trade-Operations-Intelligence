package com.fxtradeops.riskcalc.application;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.risk.ContributingFactor;
import com.fxtradeops.domain.risk.RiskCalculationRequest;
import com.fxtradeops.riskcalc.domain.RiskComputationResult;
import com.fxtradeops.riskcalc.domain.RiskEngine;
import com.fxtradeops.riskcalc.domain.RiskFact;
import com.fxtradeops.riskcalc.persistence.RiskResultEntity;
import com.fxtradeops.riskcalc.persistence.repositories.RiskResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main orchestration service: compute → persist → aggregate → check limits → publish.
 */
@Service
public class RiskCalculationService {

    private static final Logger log = LoggerFactory.getLogger(RiskCalculationService.class);
    private static final String RESULTS_TOPIC = "fxops.risk.results";

    private final RiskEngine riskEngine;
    private final RiskResultRepository riskResultRepository;
    private final AggregationService aggregationService;
    private final LimitCheckerService limitCheckerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RiskCalculationService(RiskEngine riskEngine,
                                  RiskResultRepository riskResultRepository,
                                  AggregationService aggregationService,
                                  LimitCheckerService limitCheckerService,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.riskEngine = riskEngine;
        this.riskResultRepository = riskResultRepository;
        this.aggregationService = aggregationService;
        this.limitCheckerService = limitCheckerService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a risk calculation request end-to-end with transactional guarantees.
     */
    @Transactional("transactionManager")
    public RiskResultEntity process(RiskCalculationRequest request, BigDecimal notionalAmount,
                                    String notionalCurrency, String currencyPairCode,
                                    String baseCurrency, String quoteCurrency) {
        // Check for existing result by requestId (idempotency)
        Optional<RiskResultEntity> existing = riskResultRepository
                .findFirstByTradeIdOrderByCalculatedAtDesc(request.tradeId());
        BigDecimal previousRiskAmount = existing.map(RiskResultEntity::getRiskAmount).orElse(null);

        // Build the RiskFact
        RiskFact fact = new RiskFact(
                request.tradeId(),
                currencyPairCode,
                baseCurrency,
                quoteCurrency,
                notionalAmount,
                notionalCurrency,
                request.regionCode().name(),
                request.tradingBookId()
        );

        // Compute
        RiskComputationResult result = riskEngine.compute(fact);

        // Build entity
        String calculationId = UUID.randomUUID().toString();
        RiskResultEntity entity = new RiskResultEntity();
        entity.setCalculationId(calculationId);
        entity.setTradeId(request.tradeId());
        entity.setCorrelationId(request.correlationId());
        entity.setRiskAmount(result.riskAmount());
        entity.setRiskCurrency(notionalCurrency);
        entity.setRegionCode(request.regionCode().name());
        entity.setTradingBookId(request.tradingBookId());
        entity.setCalculatedAt(Instant.now());
        entity.setRuleVersion(result.ruleVersion());
        entity.setRiskLevel(result.riskLevel().name());

        try {
            entity.setRulesFired(objectMapper.writeValueAsString(result.rulesFired()));
            entity.setContributingFactors(objectMapper.writeValueAsString(result.factors()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize risk result details", e);
        }

        // If this is a recalculation, reverse previous contribution
        if (previousRiskAmount != null) {
            aggregationService.reverseContribution(request.tradeId());
        }

        // Persist
        riskResultRepository.save(entity);

        // Update aggregations
        aggregationService.apply(entity);

        // Check limits (record breach facts, never block)
        limitCheckerService.check(entity);

        // Publish RISK_CALCULATION_COMPLETED event
        publishCompletedEvent(entity, previousRiskAmount, request.correlationId());

        log.info("Risk calculated: tradeId={}, calculationId={}, riskAmount={}, level={}",
                request.tradeId(), calculationId, result.riskAmount(), result.riskLevel());

        return entity;
    }

    /**
     * Publish a trade-failed event when reference data cannot be resolved.
     */
    public void publishFailedEvent(String tradeId, String correlationId, String reasonCode) {
        TradeEvent failedEvent = new TradeEvent(
                UUID.randomUUID().toString(),
                tradeId,
                correlationId,
                TradeEventType.TRADE_FAILED,
                Instant.now(),
                0L,
                "risk-calculation-service",
                Map.of("reasonCode", reasonCode, "stage", "RISK_CALCULATION")
        );
        kafkaTemplate.send(RESULTS_TOPIC, tradeId, failedEvent);
        log.warn("Trade failed event published: tradeId={}, reason={}", tradeId, reasonCode);
    }

    private void publishCompletedEvent(RiskResultEntity entity, BigDecimal previousRiskAmount,
                                       String correlationId) {
        Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                "calculationId", entity.getCalculationId(),
                "riskAmount", entity.getRiskAmount().toPlainString(),
                "riskCurrency", entity.getRiskCurrency(),
                "riskLevel", entity.getRiskLevel(),
                "ruleVersion", entity.getRuleVersion()
        ));
        if (previousRiskAmount != null) {
            payload.put("previousRiskAmount", previousRiskAmount.toPlainString());
        }

        TradeEvent completedEvent = new TradeEvent(
                UUID.randomUUID().toString(),
                entity.getTradeId(),
                correlationId,
                TradeEventType.RISK_CALCULATION_COMPLETED,
                Instant.now(),
                0L,
                "risk-calculation-service",
                payload
        );
        kafkaTemplate.send(RESULTS_TOPIC, entity.getTradeId(), completedEvent);
    }
}
