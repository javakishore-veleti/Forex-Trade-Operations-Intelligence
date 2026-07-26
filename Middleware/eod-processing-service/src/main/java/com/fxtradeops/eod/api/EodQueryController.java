package com.fxtradeops.eod.api;

import com.fxtradeops.eod.api.dto.*;
import com.fxtradeops.eod.application.BlockerService;
import com.fxtradeops.eod.application.BranchCompletionService;
import com.fxtradeops.eod.application.ConsolidationService;
import com.fxtradeops.eod.application.ReadinessService;
import com.fxtradeops.eod.domain.ReadinessStatusMap;
import com.fxtradeops.eod.domain.RegionOrdering;
import com.fxtradeops.eod.integration.BusinessCalendarClient;
import com.fxtradeops.eod.persistence.BlockerEntity;
import com.fxtradeops.eod.persistence.BranchCompletionEntity;
import com.fxtradeops.eod.persistence.ConsolidationEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Query (read-only) endpoints for EOD processing — all side-effect free (GP-Rq-1.4).
 */
@RestController
@RequestMapping("/api/v1/eod")
public class EodQueryController {

    private final BranchCompletionService branchCompletionService;
    private final ReadinessService readinessService;
    private final BlockerService blockerService;
    private final ConsolidationService consolidationService;
    private final BusinessCalendarClient businessCalendarClient;
    private final RegionOrdering regionOrdering;

    public EodQueryController(BranchCompletionService branchCompletionService,
                              ReadinessService readinessService,
                              BlockerService blockerService,
                              ConsolidationService consolidationService,
                              BusinessCalendarClient businessCalendarClient,
                              RegionOrdering regionOrdering) {
        this.branchCompletionService = branchCompletionService;
        this.readinessService = readinessService;
        this.blockerService = blockerService;
        this.consolidationService = consolidationService;
        this.businessCalendarClient = businessCalendarClient;
        this.regionOrdering = regionOrdering;
    }

    /**
     * GET /api/v1/eod/branches/{region} — branch completion status.
     */
    @GetMapping("/branches/{region}")
    public ResponseEntity<List<BranchStatusView>> getBranches(@PathVariable String region) {
        validateRegion(region);
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        List<BranchCompletionEntity> branches = branchCompletionService.readBranches(businessDate, region);
        List<BranchStatusView> views = branches.stream()
                .map(b -> new BranchStatusView(b.getBranchId(), b.getRegionCode(), b.isComplete(), b.getCompletedAt()))
                .toList();
        return ResponseEntity.ok(views);
    }

    /**
     * GET /api/v1/eod/readiness — Readiness Status Map for all regions + GLOBAL.
     */
    @GetMapping("/readiness")
    public ResponseEntity<ReadinessMapView> getReadiness() {
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        ReadinessStatusMap statusMap = readinessService.getReadinessStatusMap(businessDate);
        Map<String, String> regionStrings = new LinkedHashMap<>();
        statusMap.regionStatuses().forEach((k, v) -> regionStrings.put(k, v.name()));
        return ResponseEntity.ok(new ReadinessMapView(regionStrings, statusMap.globalStatus().name()));
    }

    /**
     * GET /api/v1/eod/regions/{region}/blockers — current blockers for a region.
     */
    @GetMapping("/regions/{region}/blockers")
    public ResponseEntity<List<BlockerView>> getBlockers(@PathVariable String region) {
        validateRegion(region);
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        List<BlockerEntity> blockers = blockerService.getAllBlockers(businessDate, region);
        List<BlockerView> views = blockers.stream()
                .map(b -> new BlockerView(
                        b.getBlockerId(),
                        b.getBlockerType().name(),
                        b.getReference(),
                        b.isResolved(),
                        b.getApprovalReference(),
                        b.getDetectedAt(),
                        b.getResolvedAt()))
                .toList();
        return ResponseEntity.ok(views);
    }

    /**
     * GET /api/v1/eod/consolidation — consolidation status for the current business date.
     */
    @GetMapping("/consolidation")
    public ResponseEntity<ConsolidationView> getConsolidation() {
        LocalDate businessDate = businessCalendarClient.currentGlobalBusinessDate();
        Optional<ConsolidationEntity> entity = consolidationService.getConsolidation(businessDate);
        if (entity.isEmpty()) {
            return ResponseEntity.ok(new ConsolidationView(businessDate, "NOT_READY", null, null, null));
        }
        ConsolidationEntity c = entity.get();
        return ResponseEntity.ok(new ConsolidationView(
                c.getBusinessDate(), c.getStatus(), c.getContributingRegions(),
                c.getAppliedExceptions(), c.getConsolidatedAt()));
    }

    private void validateRegion(String region) {
        if (!regionOrdering.isValidRegion(region)) {
            throw new EodCommandController.UnknownRegionException(region);
        }
    }
}
