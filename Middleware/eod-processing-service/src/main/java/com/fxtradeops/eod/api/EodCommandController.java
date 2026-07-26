package com.fxtradeops.eod.api;

import com.fxtradeops.eod.api.dto.ConsolidationView;
import com.fxtradeops.eod.api.dto.ExceptionRequest;
import com.fxtradeops.eod.application.BranchCompletionService;
import com.fxtradeops.eod.application.ConsolidationService;
import com.fxtradeops.eod.application.ExceptionService;
import com.fxtradeops.eod.application.ReadinessService;
import com.fxtradeops.eod.domain.ReadinessResult;
import com.fxtradeops.eod.domain.RegionOrdering;
import com.fxtradeops.eod.integration.BusinessCalendarClient;
import com.fxtradeops.eod.persistence.BranchCompletionEntity;
import com.fxtradeops.eod.persistence.ConsolidationEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Command endpoints for EOD processing.
 */
@RestController
@RequestMapping("/api/v1/eod")
public class EodCommandController {

    private final BranchCompletionService branchCompletionService;
    private final ReadinessService readinessService;
    private final ExceptionService exceptionService;
    private final ConsolidationService consolidationService;
    private final BusinessCalendarClient businessCalendarClient;
    private final RegionOrdering regionOrdering;

    public EodCommandController(BranchCompletionService branchCompletionService,
                                ReadinessService readinessService,
                                ExceptionService exceptionService,
                                ConsolidationService consolidationService,
                                BusinessCalendarClient businessCalendarClient,
                                RegionOrdering regionOrdering) {
        this.branchCompletionService = branchCompletionService;
        this.readinessService = readinessService;
        this.exceptionService = exceptionService;
        this.consolidationService = consolidationService;
        this.businessCalendarClient = businessCalendarClient;
        this.regionOrdering = regionOrdering;
    }

    /**
     * POST /api/v1/eod/branches/{region}/{branchId}/complete — mark branch complete (idempotent).
     */
    @PostMapping("/branches/{region}/{branchId}/complete")
    public ResponseEntity<Map<String, Object>> markBranchComplete(
            @PathVariable String region, @PathVariable String branchId) {
        validateRegion(region);
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        BranchCompletionEntity entity = branchCompletionService.markComplete(businessDate, region, branchId);
        return ResponseEntity.ok(Map.of(
                "businessDate", businessDate.toString(),
                "region", region,
                "branchId", branchId,
                "complete", entity.isComplete()
        ));
    }

    /**
     * POST /api/v1/eod/regions/{region}/rerun — rerun readiness evaluation.
     */
    @PostMapping("/regions/{region}/rerun")
    public ResponseEntity<Map<String, Object>> rerunReadiness(@PathVariable String region) {
        validateRegion(region);
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        ReadinessResult result = readinessService.rerun(businessDate, region);
        return ResponseEntity.ok(Map.of(
                "region", region,
                "status", result.status().name(),
                "unmet", result.unmet().stream()
                        .map(b -> b.type() + ":" + b.detail())
                        .toList()
        ));
    }

    /**
     * POST /api/v1/eod/regions/{region}/exceptions — apply approved exception.
     */
    @PostMapping("/regions/{region}/exceptions")
    public ResponseEntity<Map<String, Object>> applyException(
            @PathVariable String region, @RequestBody ExceptionRequest request) {
        validateRegion(region);
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        exceptionService.recordException(businessDate, region, request.blockerId(), request.approvalReference());
        return ResponseEntity.ok(Map.of(
                "region", region,
                "blockerId", request.blockerId(),
                "status", "EXCEPTION_APPLIED"
        ));
    }

    /**
     * POST /api/v1/eod/consolidate — trigger global consolidation.
     */
    @PostMapping("/consolidate")
    public ResponseEntity<ConsolidationView> consolidate() {
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        ConsolidationEntity entity = consolidationService.consolidate(businessDate);
        return ResponseEntity.ok(new ConsolidationView(
                entity.getBusinessDate(),
                entity.getStatus(),
                entity.getContributingRegions(),
                entity.getAppliedExceptions(),
                entity.getConsolidatedAt()
        ));
    }

    private void validateRegion(String region) {
        if (!regionOrdering.isValidRegion(region)) {
            throw new UnknownRegionException(region);
        }
    }

    public static class UnknownRegionException extends RuntimeException {
        public UnknownRegionException(String region) {
            super("Unknown region: " + region);
        }
    }
}
