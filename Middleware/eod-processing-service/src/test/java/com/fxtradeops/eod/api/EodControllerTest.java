package com.fxtradeops.eod.api;

import com.fxtradeops.eod.application.*;
import com.fxtradeops.eod.config.SecurityConfig;
import com.fxtradeops.eod.domain.ReadinessResult;
import com.fxtradeops.eod.domain.ReadinessStatusMap;
import com.fxtradeops.eod.domain.RegionOrdering;
import com.fxtradeops.eod.domain.RegionalCloseStatus;
import com.fxtradeops.eod.integration.BusinessCalendarClient;
import com.fxtradeops.eod.persistence.BlockerEntity;
import com.fxtradeops.eod.persistence.BranchCompletionEntity;
import com.fxtradeops.eod.persistence.ConsolidationEntity;
import com.fxtradeops.eod.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for EOD API endpoints.
 * All fixtures use FX- prefixed IDs and fictional region names.
 */
@WebMvcTest({EodCommandController.class, EodQueryController.class})
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class EodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BranchCompletionService branchCompletionService;

    @MockBean
    private ReadinessService readinessService;

    @MockBean
    private ExceptionService exceptionService;

    @MockBean
    private ConsolidationService consolidationService;

    @MockBean
    private BlockerService blockerService;

    @MockBean
    private BusinessCalendarClient businessCalendarClient;

    @MockBean
    private RegionOrdering regionOrdering;

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 25);

    @BeforeEach
    void setUp() {
        when(businessCalendarClient.currentGlobalBusinessDate()).thenReturn(BUSINESS_DATE);
        when(regionOrdering.isValidRegion("APAC")).thenReturn(true);
        when(regionOrdering.isValidRegion("EMEA")).thenReturn(true);
        when(regionOrdering.isValidRegion("AMERICAS")).thenReturn(true);
        when(regionOrdering.isValidRegion("UNKNOWN")).thenReturn(false);
        when(regionOrdering.getRegionOrder()).thenReturn(List.of("APAC", "EMEA", "AMERICAS"));
    }

    @Test
    @DisplayName("POST /branches/{region}/{branchId}/complete — 200")
    void markBranchComplete_success() throws Exception {
        BranchCompletionEntity entity = new BranchCompletionEntity(BUSINESS_DATE, "APAC", "FX-BR-001");
        when(branchCompletionService.markComplete(BUSINESS_DATE, "APAC", "FX-BR-001")).thenReturn(entity);

        mockMvc.perform(post("/api/v1/eod/branches/APAC/FX-BR-001/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("APAC"))
                .andExpect(jsonPath("$.branchId").value("FX-BR-001"))
                .andExpect(jsonPath("$.complete").value(true));
    }

    @Test
    @DisplayName("POST /branches/{region}/{branchId}/complete — 404 for unknown region")
    void markBranchComplete_unknownRegion() throws Exception {
        mockMvc.perform(post("/api/v1/eod/branches/UNKNOWN/FX-BR-001/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /regions/{region}/exceptions — 400 for blank approvalReference")
    void applyException_blankApprovalReference() throws Exception {
        doThrow(new IllegalArgumentException("approvalReference must not be blank"))
                .when(exceptionService).recordException(any(), eq("APAC"), eq("FX-BLK-001"), eq(""));

        mockMvc.perform(post("/api/v1/eod/regions/APAC/exceptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockerId\":\"FX-BLK-001\",\"approvalReference\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("approvalReference must not be blank"));
    }

    @Test
    @DisplayName("POST /regions/{region}/exceptions — 400 for null approvalReference")
    void applyException_nullApprovalReference() throws Exception {
        doThrow(new IllegalArgumentException("approvalReference must not be blank"))
                .when(exceptionService).recordException(any(), eq("EMEA"), eq("FX-BLK-002"), isNull());

        mockMvc.perform(post("/api/v1/eod/regions/EMEA/exceptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockerId\":\"FX-BLK-002\",\"approvalReference\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /consolidate — 409 when regions not ready")
    void consolidate_notReady() throws Exception {
        Map<String, List<String>> blockers = Map.of("EMEA", List.of("LATE_TRADE:FX-000123"));
        doThrow(new ConsolidationService.NotReadyException(List.of("EMEA"), blockers))
                .when(consolidationService).consolidate(BUSINESS_DATE);

        mockMvc.perform(post("/api/v1/eod/consolidate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.notReadyRegions[0]").value("EMEA"));
    }

    @Test
    @DisplayName("POST /consolidate — 200 success")
    void consolidate_success() throws Exception {
        ConsolidationEntity entity = new ConsolidationEntity(BUSINESS_DATE, "CLOSED", "APAC:CLOSED,EMEA:CLOSED,AMERICAS:CLOSED", "[]");
        when(consolidationService.consolidate(BUSINESS_DATE)).thenReturn(entity);

        mockMvc.perform(post("/api/v1/eod/consolidate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value("2026-07-25"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("GET /readiness — returns status map")
    void getReadiness() throws Exception {
        Map<String, RegionalCloseStatus> regions = new LinkedHashMap<>();
        regions.put("APAC", RegionalCloseStatus.READY);
        regions.put("EMEA", RegionalCloseStatus.BLOCKED);
        regions.put("AMERICAS", RegionalCloseStatus.IN_PROGRESS);
        ReadinessStatusMap statusMap = new ReadinessStatusMap(regions, RegionalCloseStatus.IN_PROGRESS);
        when(readinessService.getReadinessStatusMap(BUSINESS_DATE)).thenReturn(statusMap);

        mockMvc.perform(get("/api/v1/eod/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions.APAC").value("READY"))
                .andExpect(jsonPath("$.regions.EMEA").value("BLOCKED"))
                .andExpect(jsonPath("$.globalStatus").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("GET /regions/{region}/blockers — returns blocker list")
    void getBlockers() throws Exception {
        BlockerEntity blocker = new BlockerEntity("FX-BLK-001", BUSINESS_DATE, "APAC",
                com.fxtradeops.eod.domain.BlockerType.LATE_TRADE, "FX-000789");
        when(blockerService.getAllBlockers(BUSINESS_DATE, "APAC")).thenReturn(List.of(blocker));

        mockMvc.perform(get("/api/v1/eod/regions/APAC/blockers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].blockerId").value("FX-BLK-001"))
                .andExpect(jsonPath("$[0].blockerType").value("LATE_TRADE"));
    }

    @Test
    @DisplayName("GET /regions/{region}/blockers — 404 for unknown region")
    void getBlockers_unknownRegion() throws Exception {
        mockMvc.perform(get("/api/v1/eod/regions/UNKNOWN/blockers"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /regions/{region}/rerun — returns updated status")
    void rerunReadiness() throws Exception {
        when(readinessService.rerun(BUSINESS_DATE, "APAC")).thenReturn(ReadinessResult.ready());

        mockMvc.perform(post("/api/v1/eod/regions/APAC/rerun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.unmet").isEmpty());
    }

    @Test
    @DisplayName("GET /consolidation — returns NOT_READY when no consolidation")
    void getConsolidation_notReady() throws Exception {
        when(consolidationService.getConsolidation(BUSINESS_DATE)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/eod/consolidation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_READY"));
    }
}
