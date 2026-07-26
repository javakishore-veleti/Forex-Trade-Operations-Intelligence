package com.fxtradeops.riskcalc;

import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.risk.RiskCalculationRequest;
import com.fxtradeops.riskcalc.application.AggregationService;
import com.fxtradeops.riskcalc.application.DedupService;
import com.fxtradeops.riskcalc.application.RiskCalculationService;
import com.fxtradeops.riskcalc.persistence.RiskAggregationEntity;
import com.fxtradeops.riskcalc.persistence.RiskResultEntity;
import com.fxtradeops.riskcalc.persistence.repositories.RiskAggregationRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for the risk calculation flow using H2 for persistence
 * and mocked Kafka/Redis. Uses FX- prefixed synthetic IDs.
 */
@SpringBootTest(classes = RiskCalculationApplication.class)
@EnableAutoConfiguration(exclude = {KafkaAutoConfiguration.class, RedisAutoConfiguration.class})
@ActiveProfiles("test")
class RiskCalculationIntegrationTest {

    @Autowired
    private RiskCalculationService riskCalculationService;

    @Autowired
    private RiskResultRepository riskResultRepository;

    @Autowired
    private RiskAggregationRepository aggregationRepository;

    @Autowired
    private AggregationService aggregationService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private DedupService dedupService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void process_persistsResultAndUpdatesAggregations() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
        when(dedupService.seen(anyString())).thenReturn(false);

        RiskCalculationRequest request = new RiskCalculationRequest(
                "FX-000010", "FX-CORR-010", "FX-EVT-010",
                RegionCode.EMEA, "FX-BOOK-010", Instant.now(), 1
        );

        RiskResultEntity result = riskCalculationService.process(
                request, new BigDecimal("1000000"), "USD",
                "EURUSD", "EUR", "USD"
        );

        // Verify result persisted
        assertNotNull(result.getCalculationId());
        Optional<RiskResultEntity> persisted = riskResultRepository.findByCalculationId(result.getCalculationId());
        assertTrue(persisted.isPresent());
        assertEquals("FX-000010", persisted.get().getTradeId());
        assertTrue(persisted.get().getRiskAmount().compareTo(BigDecimal.ZERO) > 0);

        // Verify aggregations updated (trade count >= 1 due to shared H2)
        Optional<RiskAggregationEntity> regionAgg = aggregationRepository
                .findByScopeTypeAndScopeId("REGION", "EMEA");
        assertTrue(regionAgg.isPresent());
        assertTrue(regionAgg.get().getTotalRiskAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(regionAgg.get().getTradeCount() >= 1);
    }

    @Test
    void reverseContribution_reducesAggregation() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        RiskCalculationRequest request = new RiskCalculationRequest(
                "FX-000020", "FX-CORR-020", "FX-EVT-020",
                RegionCode.APAC, "FX-BOOK-020", Instant.now(), 1
        );

        // Process first
        riskCalculationService.process(
                request, new BigDecimal("2000000"), "USD",
                "USDJPY", "USD", "JPY"
        );

        // Get aggregation before reversal
        Optional<RiskAggregationEntity> before = aggregationRepository
                .findByScopeTypeAndScopeId("BOOK", "FX-BOOK-020");
        assertTrue(before.isPresent());
        BigDecimal amountBefore = before.get().getTotalRiskAmount();
        assertTrue(amountBefore.compareTo(BigDecimal.ZERO) > 0);

        // Reverse (simulates TRADE_CANCELLED)
        aggregationService.reverseContribution("FX-000020");

        // Verify aggregation reduced
        Optional<RiskAggregationEntity> after = aggregationRepository
                .findByScopeTypeAndScopeId("BOOK", "FX-BOOK-020");
        assertTrue(after.isPresent());
        assertEquals(0, after.get().getTotalRiskAmount().compareTo(BigDecimal.ZERO),
                "Aggregation should net to zero after reversal");
        assertEquals(0, after.get().getTradeCount(),
                "Trade count should be zero after reversal");
    }

    @Test
    void determinism_sameInputProducesSameResult() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        // Use different tradeIds to avoid hitting existing results
        RiskCalculationRequest request1 = new RiskCalculationRequest(
                "FX-000030", "FX-CORR-030", "FX-EVT-030",
                RegionCode.EMEA, "FX-BOOK-030", Instant.now(), 1
        );
        RiskCalculationRequest request2 = new RiskCalculationRequest(
                "FX-000031", "FX-CORR-031", "FX-EVT-031",
                RegionCode.EMEA, "FX-BOOK-030", Instant.now(), 1
        );

        RiskResultEntity result1 = riskCalculationService.process(
                request1, new BigDecimal("1500000"), "USD",
                "GBPUSD", "GBP", "USD"
        );
        RiskResultEntity result2 = riskCalculationService.process(
                request2, new BigDecimal("1500000"), "USD",
                "GBPUSD", "GBP", "USD"
        );

        assertEquals(result1.getRiskAmount(), result2.getRiskAmount());
        assertEquals(result1.getRiskLevel(), result2.getRiskLevel());
    }
}
