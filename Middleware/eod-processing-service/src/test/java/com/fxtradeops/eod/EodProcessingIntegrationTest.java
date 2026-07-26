package com.fxtradeops.eod;

import com.fxtradeops.eod.application.BranchCompletionService;
import com.fxtradeops.eod.application.ConsolidationService;
import com.fxtradeops.eod.application.ExceptionService;
import com.fxtradeops.eod.application.ReadinessService;
import com.fxtradeops.eod.domain.ReadinessResult;
import com.fxtradeops.eod.domain.RegionalCloseStatus;
import com.fxtradeops.eod.integration.BusinessCalendarClient;
import com.fxtradeops.eod.integration.RiskCalculationClient;
import com.fxtradeops.eod.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests with H2 (peers stubbed, Kafka mocked).
 * All fixtures use FX- prefixed IDs and fictional region/branch names.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EodProcessingIntegrationTest {

    @MockBean
    private BusinessCalendarClient businessCalendarClient;

    @MockBean
    private RiskCalculationClient riskCalculationClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private BranchCompletionService branchCompletionService;

    @Autowired
    private ReadinessService readinessService;

    @Autowired
    private ConsolidationService consolidationService;

    @Autowired
    private ExceptionService exceptionService;

    @Autowired
    private RegionalCloseRepository regionalCloseRepository;

    @Autowired
    private ConsolidationRepository consolidationRepository;

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 25);

    @BeforeEach
    void setUp() {
        when(businessCalendarClient.currentGlobalBusinessDate()).thenReturn(BUSINESS_DATE);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
    }

    @Test
    @Order(1)
    @DisplayName("Req 7.1: Region reaches READY only when all readiness inputs satisfied")
    void regionReachesReady_onlyWhenAllInputsSatisfied() {
        // Setup: risk snapshot exists for APAC
        when(riskCalculationClient.snapshotExists("APAC", BUSINESS_DATE)).thenReturn(true);

        // Mark branches complete for APAC
        branchCompletionService.markComplete(BUSINESS_DATE, "APAC", "FX-BR-APAC-001");
        branchCompletionService.markComplete(BUSINESS_DATE, "APAC", "FX-BR-APAC-002");

        // Evaluate — should be READY
        ReadinessResult result = readinessService.evaluateRegion(BUSINESS_DATE, "APAC");
        assertThat(result.status()).isEqualTo(RegionalCloseStatus.READY);
    }

    @Test
    @Order(2)
    @DisplayName("Req 7.1: Region is BLOCKED when risk snapshot missing")
    void regionBlocked_whenRiskSnapshotMissing() {
        // No risk snapshot for EMEA
        when(riskCalculationClient.snapshotExists("EMEA", BUSINESS_DATE)).thenReturn(false);

        // Mark branches for EMEA
        branchCompletionService.markComplete(BUSINESS_DATE, "EMEA", "FX-BR-EMEA-001");

        ReadinessResult result = readinessService.evaluateRegion(BUSINESS_DATE, "EMEA");
        assertThat(result.status()).isEqualTo(RegionalCloseStatus.BLOCKED);
        assertThat(result.unmet()).anyMatch(b -> b.type().name().equals("MISSING_RISK_SNAPSHOT"));
    }

    @Test
    @Order(3)
    @DisplayName("Req 7.2: Consolidation returns 409 while any region not READY")
    void consolidation_rejectsWhenNotAllReady() {
        // Setup mocks for this test
        when(riskCalculationClient.snapshotExists("APAC", BUSINESS_DATE)).thenReturn(true);
        when(riskCalculationClient.snapshotExists("EMEA", BUSINESS_DATE)).thenReturn(false);
        when(riskCalculationClient.snapshotExists("AMERICAS", BUSINESS_DATE)).thenReturn(true);

        branchCompletionService.markComplete(BUSINESS_DATE, "AMERICAS", "FX-BR-AMR-001");

        // Evaluate to set the statuses
        readinessService.evaluateRegion(BUSINESS_DATE, "APAC");
        readinessService.evaluateRegion(BUSINESS_DATE, "EMEA");
        readinessService.evaluateRegion(BUSINESS_DATE, "AMERICAS");

        assertThatThrownBy(() -> consolidationService.consolidate(BUSINESS_DATE))
                .isInstanceOf(ConsolidationService.NotReadyException.class);
    }

    @Test
    @Order(4)
    @DisplayName("Req 7.2: Consolidation succeeds when ALL regions READY")
    void consolidation_succeedsWhenAllReady() {
        // Make all regions ready
        when(riskCalculationClient.snapshotExists("APAC", BUSINESS_DATE)).thenReturn(true);
        when(riskCalculationClient.snapshotExists("EMEA", BUSINESS_DATE)).thenReturn(true);
        when(riskCalculationClient.snapshotExists("AMERICAS", BUSINESS_DATE)).thenReturn(true);

        readinessService.evaluateRegion(BUSINESS_DATE, "APAC");
        readinessService.evaluateRegion(BUSINESS_DATE, "EMEA");
        readinessService.evaluateRegion(BUSINESS_DATE, "AMERICAS");

        // Now all should be READY — consolidation should succeed
        ConsolidationEntity result = consolidationService.consolidate(BUSINESS_DATE);
        assertThat(result.getStatus()).isEqualTo("CLOSED");
        assertThat(result.getBusinessDate()).isEqualTo(BUSINESS_DATE);

        // Verify regions are now CLOSED
        Optional<RegionalCloseEntity> apac = regionalCloseRepository.findByBusinessDateAndRegionCode(BUSINESS_DATE, "APAC");
        assertThat(apac).isPresent();
        assertThat(apac.get().getStatus()).isEqualTo(RegionalCloseStatus.CLOSED);
    }

    @Test
    @Order(5)
    @DisplayName("Req 7.3: Repeat consolidation returns existing result (idempotent)")
    void consolidation_idempotent() {
        // Already CLOSED from test 4
        ConsolidationEntity result = consolidationService.consolidate(BUSINESS_DATE);
        assertThat(result.getStatus()).isEqualTo("CLOSED");
        assertThat(result.getBusinessDate()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    @Order(6)
    @DisplayName("Req 7.4: Exception without approvalReference is rejected")
    void exception_withoutApprovalReference_rejected() {
        assertThatThrownBy(() ->
                exceptionService.recordException(BUSINESS_DATE, "APAC", "FX-BLK-001", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approvalReference must not be blank");

        assertThatThrownBy(() ->
                exceptionService.recordException(BUSINESS_DATE, "APAC", "FX-BLK-001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approvalReference must not be blank");
    }
}
