package com.fxtradeops.tradelifecycle.api;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.tradelifecycle.config.SecurityConfig;
import com.fxtradeops.tradelifecycle.persistence.document.AuditEntryDocument;
import com.fxtradeops.tradelifecycle.persistence.document.AuditRepository;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateEntity;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for LifecycleQueryController.
 * Uses synthetic FX- prefixed IDs.
 */
@WebMvcTest(LifecycleQueryController.class)
@Import(SecurityConfig.class)
class LifecycleQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeCurrentStateRepository stateRepository;

    @MockBean
    private AuditRepository auditRepository;

    @Test
    @DisplayName("GET /state returns current state for known trade")
    void getState_knownTrade_returnsState() throws Exception {
        TradeCurrentStateEntity entity = new TradeCurrentStateEntity(
                "FX-000001", TradeStatus.VALIDATED, "corr-123", Instant.parse("2025-01-15T10:00:00Z"));
        when(stateRepository.findById("FX-000001")).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/v1/trades/FX-000001/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("FX-000001"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    @DisplayName("GET /state returns 404 for unknown trade")
    void getState_unknownTrade_returns404() throws Exception {
        when(stateRepository.findById("FX-999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trades/FX-999999/state"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /timeline includes rejected and noop entries (anomalies visible)")
    void getTimeline_includesAnomalies() throws Exception {
        when(stateRepository.existsById("FX-000002")).thenReturn(true);

        AuditEntryDocument captured = buildAudit("FX-000002", "TRADE_CAPTURED", null, "CAPTURED", false, false, false);
        AuditEntryDocument rejected = buildAudit("FX-000002", "TRADE_SETTLED", "CAPTURED", "SETTLED", true, false, false);
        AuditEntryDocument noop = buildAudit("FX-000002", "TRADE_CAPTURED", "CAPTURED", "CAPTURED", false, true, false);

        when(auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc("FX-000002"))
                .thenReturn(List.of(captured, rejected, noop));

        mockMvc.perform(get("/api/v1/trades/FX-000002/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].rejected").value(true))
                .andExpect(jsonPath("$[2].noop").value(true));
    }

    @Test
    @DisplayName("GET /timeline returns 404 for unknown trade")
    void getTimeline_unknownTrade_returns404() throws Exception {
        when(stateRepository.existsById("FX-999999")).thenReturn(false);
        when(auditRepository.existsByTradeId("FX-999999")).thenReturn(false);

        mockMvc.perform(get("/api/v1/trades/FX-999999/timeline"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /expected-lifecycle shows reached and pending steps")
    void getExpectedLifecycle_showsReachedAndPending() throws Exception {
        TradeCurrentStateEntity entity = new TradeCurrentStateEntity(
                "FX-000003", TradeStatus.ENRICHED, "corr-456", Instant.now());
        when(stateRepository.findById("FX-000003")).thenReturn(Optional.of(entity));

        AuditEntryDocument a1 = buildAudit("FX-000003", "TRADE_CAPTURED", null, "CAPTURED", false, false, false);
        AuditEntryDocument a2 = buildAudit("FX-000003", "TRADE_VALIDATED", "CAPTURED", "VALIDATED", false, false, false);
        AuditEntryDocument a3 = buildAudit("FX-000003", "TRADE_ENRICHED", "VALIDATED", "ENRICHED", false, false, false);

        when(auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc("FX-000003"))
                .thenReturn(List.of(a1, a2, a3));

        mockMvc.perform(get("/api/v1/trades/FX-000003/expected-lifecycle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("FX-000003"))
                .andExpect(jsonPath("$.steps[0].status").value("CAPTURED"))
                .andExpect(jsonPath("$.steps[0].reached").value(true))
                .andExpect(jsonPath("$.steps[2].status").value("ENRICHED"))
                .andExpect(jsonPath("$.steps[2].reached").value(true))
                .andExpect(jsonPath("$.steps[3].status").value("RISK_CALCULATED"))
                .andExpect(jsonPath("$.steps[3].reached").value(false));
    }

    @Test
    @DisplayName("GET /expected-lifecycle returns 404 for unknown trade")
    void getExpectedLifecycle_unknownTrade_returns404() throws Exception {
        when(stateRepository.findById("FX-999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trades/FX-999999/expected-lifecycle"))
                .andExpect(status().isNotFound());
    }

    private AuditEntryDocument buildAudit(String tradeId, String eventType, String from, String to,
                                          boolean rejected, boolean noop, boolean orphan) {
        AuditEntryDocument doc = new AuditEntryDocument();
        doc.setTradeId(tradeId);
        doc.setCorrelationId("corr-test");
        doc.setEventId("evt-" + System.nanoTime());
        doc.setEventType(eventType);
        doc.setFromStatus(from);
        doc.setToStatus(to);
        doc.setRejected(rejected);
        doc.setNoop(noop);
        doc.setOrphan(orphan);
        doc.setSourceService("test-service");
        doc.setOccurredAt(Instant.now());
        doc.setRecordedAt(Instant.now());
        return doc;
    }
}
