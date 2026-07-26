package com.fxtradeops.riskcalc.api;

import com.fxtradeops.riskcalc.application.RiskCalculationService;
import com.fxtradeops.riskcalc.persistence.LimitBreachEntity;
import com.fxtradeops.riskcalc.persistence.RiskAggregationEntity;
import com.fxtradeops.riskcalc.persistence.RiskResultEntity;
import com.fxtradeops.riskcalc.persistence.repositories.LimitBreachRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskAggregationRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for RiskQueryController.
 * All fixtures use synthetic FX- prefixed IDs.
 */
@WebMvcTest(RiskQueryController.class)
@Import(com.fxtradeops.riskcalc.config.SecurityConfig.class)
class RiskQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RiskResultRepository riskResultRepository;

    @MockBean
    private RiskAggregationRepository aggregationRepository;

    @MockBean
    private LimitBreachRepository limitBreachRepository;

    @MockBean
    private RiskCalculationService riskCalculationService;

    @Test
    void getLatestResult_found() throws Exception {
        RiskResultEntity entity = createSampleResult();
        when(riskResultRepository.findFirstByTradeIdOrderByCalculatedAtDesc("FX-000001"))
                .thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/v1/risk/FX-000001/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("FX-000001"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));
    }

    @Test
    void getLatestResult_notFound() throws Exception {
        when(riskResultRepository.findFirstByTradeIdOrderByCalculatedAtDesc("FX-999999"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/risk/FX-999999/result"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAggregation_found() throws Exception {
        RiskAggregationEntity agg = new RiskAggregationEntity();
        agg.setScopeType("REGION");
        agg.setScopeId("APAC");
        agg.setTotalRiskAmount(new BigDecimal("150000.0000"));
        agg.setRiskCurrency("USD");
        agg.setTradeCount(5);
        agg.setLastUpdatedAt(Instant.now());

        when(aggregationRepository.findByScopeTypeAndScopeId("REGION", "APAC"))
                .thenReturn(Optional.of(agg));

        mockMvc.perform(get("/api/v1/risk/aggregation?scope=REGION&id=APAC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("REGION"))
                .andExpect(jsonPath("$.tradeCount").value(5));
    }

    @Test
    void getBreaches_returnsListForScope() throws Exception {
        LimitBreachEntity breach = new LimitBreachEntity();
        breach.setBreachId("FX-BREACH-001");
        breach.setCalculationId("FX-CALC-001");
        breach.setScopeType("REGION");
        breach.setScopeId("APAC");
        breach.setLimitAmount(new BigDecimal("100000.0000"));
        breach.setObservedAmount(new BigDecimal("150000.0000"));
        breach.setDetectedAt(Instant.now());

        when(limitBreachRepository.findByScopeTypeAndScopeId("REGION", "APAC"))
                .thenReturn(List.of(breach));

        mockMvc.perform(get("/api/v1/risk/limits/breaches?scope=REGION&id=APAC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].breachId").value("FX-BREACH-001"));
    }

    @Test
    void calculate_onDemand() throws Exception {
        RiskResultEntity entity = createSampleResult();
        when(riskCalculationService.process(any(), any(), any(), any(), any(), any()))
                .thenReturn(entity);

        String body = """
                {
                    "requestId": "FX-REQ-001",
                    "tradeId": "FX-000001",
                    "regionCode": "EMEA",
                    "tradingBookId": "FX-BOOK-001",
                    "currencyPairCode": "EURUSD",
                    "baseCurrency": "EUR",
                    "quoteCurrency": "USD",
                    "notionalAmount": 1000000,
                    "notionalCurrency": "USD"
                }
                """;

        mockMvc.perform(post("/api/v1/risk/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationId").value("FX-CALC-001"));
    }

    private RiskResultEntity createSampleResult() {
        RiskResultEntity entity = new RiskResultEntity();
        entity.setCalculationId("FX-CALC-001");
        entity.setTradeId("FX-000001");
        entity.setCorrelationId("FX-CORR-001");
        entity.setRiskAmount(new BigDecimal("75000.0000"));
        entity.setRiskCurrency("USD");
        entity.setRegionCode("EMEA");
        entity.setTradingBookId("FX-BOOK-001");
        entity.setCalculatedAt(Instant.now());
        entity.setRuleVersion("RULES-7.14");
        entity.setRiskLevel("MEDIUM");
        entity.setRulesFired("[\"FX-PAIR-EURUSD-001\"]");
        entity.setContributingFactors("[]");
        return entity;
    }
}
