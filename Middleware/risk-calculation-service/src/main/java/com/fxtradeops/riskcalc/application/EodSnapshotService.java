package com.fxtradeops.riskcalc.application;

import com.fxtradeops.riskcalc.persistence.EodSnapshotEntity;
import com.fxtradeops.riskcalc.persistence.RiskAggregationEntity;
import com.fxtradeops.riskcalc.persistence.repositories.EodSnapshotRepository;
import com.fxtradeops.riskcalc.persistence.repositories.RiskAggregationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * EOD snapshot finalization service.
 * Idempotent per (scope, businessDate): re-snapshots overwrite deterministically.
 */
@Service
public class EodSnapshotService {

    private final EodSnapshotRepository eodSnapshotRepository;
    private final RiskAggregationRepository aggregationRepository;
    private final String ruleVersion;

    public EodSnapshotService(EodSnapshotRepository eodSnapshotRepository,
                              RiskAggregationRepository aggregationRepository,
                              String ruleVersion) {
        this.eodSnapshotRepository = eodSnapshotRepository;
        this.aggregationRepository = aggregationRepository;
        this.ruleVersion = ruleVersion;
    }

    /**
     * Finalize an EOD snapshot for the given scope and business date.
     * Idempotent: overwrites existing snapshot for same (scope, date).
     */
    @Transactional
    public EodSnapshotEntity snapshot(String scopeType, String scopeId, LocalDate businessDate) {
        Optional<RiskAggregationEntity> aggregation = aggregationRepository
                .findByScopeTypeAndScopeId(scopeType, scopeId);

        BigDecimal totalRiskAmount = aggregation.map(RiskAggregationEntity::getTotalRiskAmount)
                .orElse(BigDecimal.ZERO);
        int tradeCount = aggregation.map(RiskAggregationEntity::getTradeCount).orElse(0);

        // Idempotent: find existing or create new
        EodSnapshotEntity snapshot = eodSnapshotRepository
                .findByScopeTypeAndScopeIdAndBusinessDate(scopeType, scopeId, businessDate)
                .orElseGet(() -> {
                    EodSnapshotEntity newSnapshot = new EodSnapshotEntity();
                    newSnapshot.setSnapshotId(UUID.randomUUID().toString());
                    newSnapshot.setScopeType(scopeType);
                    newSnapshot.setScopeId(scopeId);
                    newSnapshot.setBusinessDate(businessDate);
                    return newSnapshot;
                });

        snapshot.setTotalRiskAmount(totalRiskAmount);
        snapshot.setTradeCount(tradeCount);
        snapshot.setRuleVersion(ruleVersion);
        snapshot.setSnapshottedAt(Instant.now());

        return eodSnapshotRepository.save(snapshot);
    }
}
