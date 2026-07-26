package com.fxtradeops.eod.application;

import com.fxtradeops.eod.domain.ReadinessResult;
import com.fxtradeops.eod.domain.RegionOrdering;
import com.fxtradeops.eod.domain.RegionalCloseStatus;
import com.fxtradeops.eod.event.EodCompletedEvent;
import com.fxtradeops.eod.persistence.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global consolidation — only when ALL prerequisite regions are READY.
 * Idempotent per Global Business Date (business_date PK).
 */
@Service
public class ConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationService.class);

    private final RegionOrdering regionOrdering;
    private final RegionalCloseRepository regionalCloseRepository;
    private final ConsolidationRepository consolidationRepository;
    private final BlockerRepository blockerRepository;
    private final EodAuditRepository eodAuditRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${eod.topics.eod-completed:eod-completed}")
    private String eodCompletedTopic;

    public ConsolidationService(RegionOrdering regionOrdering,
                                RegionalCloseRepository regionalCloseRepository,
                                ConsolidationRepository consolidationRepository,
                                BlockerRepository blockerRepository,
                                EodAuditRepository eodAuditRepository,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                MeterRegistry meterRegistry) {
        this.regionOrdering = regionOrdering;
        this.regionalCloseRepository = regionalCloseRepository;
        this.consolidationRepository = consolidationRepository;
        this.blockerRepository = blockerRepository;
        this.eodAuditRepository = eodAuditRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Consolidate for the given business date.
     * - If already CLOSED → return existing result (idempotent).
     * - If any region not READY → throw NotReadyException (409).
     * - If all READY → close all regions, record consolidation, publish event.
     */
    @Transactional
    public ConsolidationEntity consolidate(LocalDate businessDate) {
        // 1. Check if already closed (idempotent)
        Optional<ConsolidationEntity> existing = consolidationRepository.findByBusinessDate(businessDate);
        if (existing.isPresent() && "CLOSED".equals(existing.get().getStatus())) {
            log.info("Consolidation already CLOSED for date={}, returning existing result", businessDate);
            return existing.get();
        }

        // 2. Evaluate all regions
        List<String> notReadyRegions = new ArrayList<>();
        Map<String, RegionalCloseStatus> regionStatuses = new LinkedHashMap<>();

        for (String region : regionOrdering.getRegionOrder()) {
            RegionalCloseEntity closeEntity = regionalCloseRepository
                    .findByBusinessDateAndRegionCode(businessDate, region)
                    .orElse(null);

            RegionalCloseStatus status = closeEntity != null ? closeEntity.getStatus() : RegionalCloseStatus.IN_PROGRESS;
            regionStatuses.put(region, status);

            if (status != RegionalCloseStatus.READY) {
                notReadyRegions.add(region);
            }
        }

        // 3. If any not ready → reject with 409
        if (!notReadyRegions.isEmpty()) {
            Map<String, List<String>> blockerMap = new LinkedHashMap<>();
            for (String region : notReadyRegions) {
                List<String> blockers = blockerRepository
                        .findByBusinessDateAndRegionCodeAndResolvedFalse(businessDate, region)
                        .stream()
                        .map(b -> b.getBlockerType() + ":" + b.getReference())
                        .toList();
                blockerMap.put(region, blockers);
            }
            meterRegistry.counter("eod_consolidation_total", "outcome", "rejected").increment();
            throw new NotReadyException(notReadyRegions, blockerMap);
        }

        // 4. All regions READY — consolidate in one transaction
        // Close each region
        for (String region : regionOrdering.getRegionOrder()) {
            RegionalCloseEntity closeEntity = regionalCloseRepository
                    .findByBusinessDateAndRegionCode(businessDate, region)
                    .orElseThrow();
            closeEntity.setStatus(RegionalCloseStatus.CLOSED);
            regionalCloseRepository.save(closeEntity);
        }

        // Collect applied exceptions
        List<String> appliedExceptions = new ArrayList<>();
        for (String region : regionOrdering.getRegionOrder()) {
            blockerRepository.findByBusinessDateAndRegionCode(businessDate, region)
                    .stream()
                    .filter(BlockerEntity::isResolved)
                    .filter(b -> b.getApprovalReference() != null)
                    .forEach(b -> appliedExceptions.add(b.getApprovalReference()));
        }

        // Create consolidation row
        ConsolidationEntity consolidation = new ConsolidationEntity(
                businessDate,
                "CLOSED",
                regionStatuses.entrySet().stream()
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(Collectors.joining(",")),
                appliedExceptions.isEmpty() ? "[]" : appliedExceptions.toString()
        );
        consolidationRepository.save(consolidation);

        // Append audit row
        EodAuditEntity audit = new EodAuditEntity(
                UUID.randomUUID().toString(),
                businessDate,
                null, // global scope
                "CONSOLIDATED",
                null,
                "All regions closed: " + regionOrdering.getRegionOrder()
        );
        eodAuditRepository.save(audit);

        // Publish EodCompletedEvent
        EodCompletedEvent event = new EodCompletedEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "eod-processing-service",
                Instant.now(),
                businessDate
        );
        kafkaTemplate.send(eodCompletedTopic, businessDate.toString(), event);

        meterRegistry.counter("eod_consolidation_total", "outcome", "success").increment();
        log.info("Global consolidation completed for date={}", businessDate);
        return consolidation;
    }

    /**
     * Get current consolidation status for a business date.
     */
    @Transactional(readOnly = true)
    public Optional<ConsolidationEntity> getConsolidation(LocalDate businessDate) {
        return consolidationRepository.findByBusinessDate(businessDate);
    }

    /**
     * Exception thrown when consolidation is attempted while regions are not ready.
     */
    public static class NotReadyException extends RuntimeException {
        private final List<String> notReadyRegions;
        private final Map<String, List<String>> blockers;

        public NotReadyException(List<String> notReadyRegions, Map<String, List<String>> blockers) {
            super("Not all regions are READY: " + notReadyRegions);
            this.notReadyRegions = notReadyRegions;
            this.blockers = blockers;
        }

        public List<String> getNotReadyRegions() { return notReadyRegions; }
        public Map<String, List<String>> getBlockers() { return blockers; }
    }
}
