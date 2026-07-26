package com.fxtradeops.eod.application;

import com.fxtradeops.eod.domain.*;
import com.fxtradeops.eod.integration.RiskCalculationClient;
import com.fxtradeops.eod.persistence.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects readiness inputs and invokes the pure ReadinessEvaluator.
 */
@Service
public class ReadinessService {

    private final BranchCompletionService branchCompletionService;
    private final BlockerService blockerService;
    private final RiskCalculationClient riskCalculationClient;
    private final RegionOrdering regionOrdering;
    private final RegionalCloseRepository regionalCloseRepository;
    private final ConsolidationRepository consolidationRepository;
    private final EodAuditRepository eodAuditRepository;
    private final MeterRegistry meterRegistry;

    public ReadinessService(BranchCompletionService branchCompletionService,
                            BlockerService blockerService,
                            RiskCalculationClient riskCalculationClient,
                            RegionOrdering regionOrdering,
                            RegionalCloseRepository regionalCloseRepository,
                            ConsolidationRepository consolidationRepository,
                            EodAuditRepository eodAuditRepository,
                            MeterRegistry meterRegistry) {
        this.branchCompletionService = branchCompletionService;
        this.blockerService = blockerService;
        this.riskCalculationClient = riskCalculationClient;
        this.regionOrdering = regionOrdering;
        this.regionalCloseRepository = regionalCloseRepository;
        this.consolidationRepository = consolidationRepository;
        this.eodAuditRepository = eodAuditRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Evaluate a region's readiness for the given business date.
     */
    @Transactional
    public ReadinessResult evaluateRegion(LocalDate businessDate, String region) {
        boolean allBranchesComplete = branchCompletionService.allBranchesComplete(businessDate, region);
        String incompleteBranches = branchCompletionService.incompleteBranches(businessDate, region);
        boolean riskSnapshotExists = riskCalculationClient.snapshotExists(region, businessDate);
        List<Blocker> openBlockers = blockerService.toOpenDomainBlockers(businessDate, region);
        int tolerance = regionOrdering.getUnprocessedTradeTolerance();

        ReadinessInputs inputs = new ReadinessInputs(
                region,
                allBranchesComplete,
                incompleteBranches,
                0, // unprocessed trade count — would come from peer service; defaulting to 0
                tolerance,
                false, // toleranceExceptionApproved — check if there's an approved exception for unprocessed
                riskSnapshotExists,
                openBlockers
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        // Persist the status
        RegionalCloseEntity closeEntity = regionalCloseRepository
                .findByBusinessDateAndRegionCode(businessDate, region)
                .orElseGet(() -> new RegionalCloseEntity(businessDate, region, RegionalCloseStatus.IN_PROGRESS));

        if (closeEntity.getStatus() != RegionalCloseStatus.CLOSED) {
            closeEntity.setStatus(result.status());
            closeEntity.setUnmetConditions(result.unmet().isEmpty() ? null :
                    result.unmet().stream()
                            .map(b -> b.type() + ":" + b.detail())
                            .reduce((a, b) -> a + "|" + b)
                            .orElse(null));
            regionalCloseRepository.save(closeEntity);
        }

        // Record metric
        meterRegistry.gauge("eod_region_readiness",
                io.micrometer.core.instrument.Tags.of("region", region, "status", result.status().name()),
                1);

        return result;
    }

    /**
     * Rerun: re-evaluate readiness for a region after a blocker is resolved.
     */
    @Transactional
    public ReadinessResult rerun(LocalDate businessDate, String region) {
        ReadinessResult result = evaluateRegion(businessDate, region);

        // Append audit row for the rerun
        EodAuditEntity audit = new EodAuditEntity(
                java.util.UUID.randomUUID().toString(),
                businessDate,
                region,
                "RERUN",
                null,
                "Readiness rerun: " + result.status()
        );
        eodAuditRepository.save(audit);

        return result;
    }

    /**
     * Build the Readiness Status Map for all regions + GLOBAL.
     */
    @Transactional(readOnly = true)
    public ReadinessStatusMap getReadinessStatusMap(LocalDate businessDate) {
        Map<String, RegionalCloseStatus> regionStatuses = new LinkedHashMap<>();

        for (String region : regionOrdering.getRegionOrder()) {
            RegionalCloseEntity entity = regionalCloseRepository
                    .findByBusinessDateAndRegionCode(businessDate, region)
                    .orElse(null);
            regionStatuses.put(region, entity != null ? entity.getStatus() : RegionalCloseStatus.IN_PROGRESS);
        }

        // GLOBAL status
        RegionalCloseStatus globalStatus = consolidationRepository.findByBusinessDate(businessDate)
                .map(c -> "CLOSED".equals(c.getStatus()) ? RegionalCloseStatus.CLOSED : RegionalCloseStatus.IN_PROGRESS)
                .orElse(RegionalCloseStatus.IN_PROGRESS);

        return new ReadinessStatusMap(regionStatuses, globalStatus);
    }
}
