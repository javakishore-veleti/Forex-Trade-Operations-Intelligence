package com.fxtradeops.reconciliation.api;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.application.ReconciliationService;
import com.fxtradeops.reconciliation.application.SweepService;
import com.fxtradeops.reconciliation.domain.action.PermittedAction;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.impact.BusinessImpact;
import com.fxtradeops.reconciliation.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for ReconciliationQueryController.
 * Verifies envelope shape, 404, and sweep responses including UNAVAILABLE sources.
 */
@WebMvcTest(ReconciliationQueryController.class)
@Import(com.fxtradeops.reconciliation.config.SecurityConfig.class)
class ReconciliationQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    @MockBean
    private SweepService sweepService;

    @Test
    @DisplayName("GET /{tradeId} returns 200 with correct envelope shape")
    void getReturnsResultWithCorrectShape() throws Exception {
        Instant now = Instant.parse("2024-06-15T10:30:00Z");
        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.BOOKED, now, true));
        states.put(SourceId.DOCUMENT, new ObservedState(SourceId.DOCUMENT, TradeStatus.BOOKED, now, true));
        states.put(SourceId.CACHE, new ObservedState(SourceId.CACHE, TradeStatus.VALIDATED, now, true));
        states.put(SourceId.EVENT_STREAM, ObservedState.unavailable(SourceId.EVENT_STREAM));

        ReconciliationResult result = new ReconciliationResult(
                "FX-000001",
                states,
                TradeStatus.BOOKED,
                DerivationResult.DerivationStatus.COMPLETE,
                List.of(new Divergence(SourceId.CACHE, TradeStatus.VALIDATED, TradeStatus.BOOKED, DivergenceClassification.STALE)),
                SourceId.CACHE,
                List.of(new ViolatedInvariant("INV_SETTLED_NOT_PENDING_IN_CACHE", "test")),
                Set.of(PermittedAction.REFRESH_CACHE),
                BusinessImpact.LOW
        );

        when(reconciliationService.reconcile("FX-000001")).thenReturn(result);

        mockMvc.perform(get("/api/v1/reconciliation/FX-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("FX-000001"))
                .andExpect(jsonPath("$.expectedState").value("BOOKED"))
                .andExpect(jsonPath("$.derivation").value("COMPLETE"))
                .andExpect(jsonPath("$.states.RELATIONAL.status").value("BOOKED"))
                .andExpect(jsonPath("$.states.RELATIONAL.available").value(true))
                .andExpect(jsonPath("$.states.EVENT_STREAM.available").value(false))
                .andExpect(jsonPath("$.divergences[0].source").value("CACHE"))
                .andExpect(jsonPath("$.divergences[0].observed").value("VALIDATED"))
                .andExpect(jsonPath("$.divergences[0].classification").value("STALE"))
                .andExpect(jsonPath("$.mostLikelyStaleSource").value("CACHE"))
                .andExpect(jsonPath("$.violatedInvariants[0].code").value("INV_SETTLED_NOT_PENDING_IN_CACHE"))
                .andExpect(jsonPath("$.permittedActions").isArray())
                .andExpect(jsonPath("$.businessImpact").value("LOW"));
    }

    @Test
    @DisplayName("GET /{tradeId} returns 404 when trade unknown")
    void getReturns404WhenUnknown() throws Exception {
        when(reconciliationService.reconcile("FX-999999")).thenReturn(null);

        mockMvc.perform(get("/api/v1/reconciliation/FX-999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /sweep returns result array")
    void sweepReturnsResultArray() throws Exception {
        Instant now = Instant.now();
        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        states.put(SourceId.RELATIONAL, new ObservedState(SourceId.RELATIONAL, TradeStatus.BOOKED, now, true));

        ReconciliationResult r = new ReconciliationResult(
                "FX-000001", states, TradeStatus.BOOKED,
                DerivationResult.DerivationStatus.COMPLETE,
                List.of(), null, List.of(),
                Set.of(PermittedAction.NO_ACTION), BusinessImpact.NONE
        );

        when(sweepService.sweep(List.of("FX-000001", "FX-000002")))
                .thenReturn(List.of(r));

        mockMvc.perform(post("/api/v1/reconciliation/sweep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tradeIds\":[\"FX-000001\",\"FX-000002\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeId").value("FX-000001"))
                .andExpect(jsonPath("$[0].businessImpact").value("NONE"));
    }

    @Test
    @DisplayName("POST /sweep with empty tradeIds returns 400")
    void sweepEmptyTradeIdsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reconciliation/sweep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tradeIds\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
