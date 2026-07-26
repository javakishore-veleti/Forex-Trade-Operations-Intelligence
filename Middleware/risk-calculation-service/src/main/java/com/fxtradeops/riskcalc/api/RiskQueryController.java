package com.fxtradeops.riskcalc.api;

import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.risk.RiskCalculationRequest;
import com.fxtradeops.riskcalc.api.dto.AggregationResponse;
import com.fxtradeops.riskcalc.api.dto.LimitBreachResponse;
import com.fxtradeops.riskcalc.api.dto.RiskCalculateRequest;
import com.fxtradeops.riskcalc.api.dto.RiskResultResponse;
import com.fxtradeops.riskcalc.application.RiskCalculationService;
import com.fxtradeops.riskcalc.persistence.LimitBreachEntity;
import com.fxtradeops.riskcalc.persistence.RiskAggregationEntity;
import com.fxtradeops.riskcalc.persistence.RiskResultEntity;
import com.fxtradeops.riskcalc.persistence.repositories.LimitBreachRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskAggregationRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST API for risk queries and on-demand calculations.
 */
@RestController
@RequestMapping("/api/v1/risk")
public class RiskQueryController {

    private final RiskResultRepository riskResultRepository;
    private final RiskAggregationRepository aggregationRepository;
    private final LimitBreachRepository limitBreachRepository;
    private final RiskCalculationService riskCalculationService;
    private final ObjectMapper objectMapper;

    public RiskQueryController(RiskResultRepository riskResultRepository,
                               RiskAggregationRepository aggregationRepository,
                               LimitBreachRepository limitBreachRepository,
                               RiskCalculationService riskCalculationService,
                               ObjectMapper objectMapper) {
        this.riskResultRepository = riskResultRepository;
        this.aggregationRepository = aggregationRepository;
        this.limitBreachRepository = limitBreachRepository;
        this.riskCalculationService = riskCalculationService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /api/v1/risk/{tradeId}/result — latest risk result for a trade.
     */
    @GetMapping("/{tradeId}/result")
    public ResponseEntity<RiskResultResponse> getLatestResult(@PathVariable String tradeId) {
        return riskResultRepository.findFirstByTradeIdOrderByCalculatedAtDesc(tradeId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/risk/{tradeId}/result/{calculationId} — specific calculation.
     */
    @GetMapping("/{tradeId}/result/{calculationId}")
    public ResponseEntity<RiskResultResponse> getResultById(@PathVariable String tradeId,
                                                            @PathVariable String calculationId) {
        return riskResultRepository.findByCalculationId(calculationId)
                .filter(r -> r.getTradeId().equals(tradeId))
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/v1/risk/calculate — on-demand risk calculation, idempotent by requestId.
     */
    @PostMapping("/calculate")
    public ResponseEntity<RiskResultResponse> calculate(@Valid @RequestBody RiskCalculateRequest request) {
        RegionCode regionCode;
        try {
            regionCode = RegionCode.valueOf(request.regionCode());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        RiskCalculationRequest calcRequest = new RiskCalculationRequest(
                request.tradeId(),
                UUID.randomUUID().toString(),
                request.requestId(),
                regionCode,
                request.tradingBookId(),
                Instant.now(),
                1
        );

        RiskResultEntity result = riskCalculationService.process(
                calcRequest,
                request.notionalAmount(),
                request.notionalCurrency(),
                request.currencyPairCode(),
                request.baseCurrency(),
                request.quoteCurrency()
        );

        return ResponseEntity.ok(toResponse(result));
    }

    /**
     * GET /api/v1/risk/aggregation?scope=REGION&id=APAC
     */
    @GetMapping("/aggregation")
    public ResponseEntity<AggregationResponse> getAggregation(
            @RequestParam String scope,
            @RequestParam String id) {
        return aggregationRepository.findByScopeTypeAndScopeId(scope, id)
                .map(agg -> new AggregationResponse(
                        agg.getScopeType(),
                        agg.getScopeId(),
                        agg.getTotalRiskAmount(),
                        agg.getRiskCurrency(),
                        agg.getTradeCount(),
                        agg.getLastUpdatedAt()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/risk/limits/breaches?scope=REGION&id=APAC
     */
    @GetMapping("/limits/breaches")
    public ResponseEntity<List<LimitBreachResponse>> getBreaches(
            @RequestParam String scope,
            @RequestParam String id) {
        List<LimitBreachResponse> breaches = limitBreachRepository
                .findByScopeTypeAndScopeId(scope, id)
                .stream()
                .map(b -> new LimitBreachResponse(
                        b.getBreachId(),
                        b.getCalculationId(),
                        b.getScopeType(),
                        b.getScopeId(),
                        b.getLimitAmount(),
                        b.getObservedAmount(),
                        b.getDetectedAt()
                ))
                .toList();
        return ResponseEntity.ok(breaches);
    }

    private RiskResultResponse toResponse(RiskResultEntity entity) {
        List<String> rulesFired = List.of();
        try {
            if (entity.getRulesFired() != null) {
                rulesFired = objectMapper.readValue(entity.getRulesFired(),
                        new TypeReference<List<String>>() {});
            }
        } catch (JsonProcessingException e) {
            // Return empty list on parse failure
        }

        return new RiskResultResponse(
                entity.getCalculationId(),
                entity.getTradeId(),
                entity.getRiskAmount(),
                entity.getRiskCurrency(),
                entity.getRegionCode(),
                entity.getTradingBookId(),
                entity.getCalculatedAt(),
                entity.getRuleVersion(),
                entity.getRiskLevel(),
                rulesFired,
                entity.getContributingFactors()
        );
    }
}
