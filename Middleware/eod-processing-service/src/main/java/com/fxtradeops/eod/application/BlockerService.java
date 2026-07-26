package com.fxtradeops.eod.application;

import com.fxtradeops.eod.domain.Blocker;
import com.fxtradeops.eod.domain.BlockerType;
import com.fxtradeops.eod.persistence.BlockerEntity;
import com.fxtradeops.eod.persistence.BlockerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tracks late trades and other blockers that prevent a region from reaching READY.
 */
@Service
public class BlockerService {

    private final BlockerRepository blockerRepository;

    public BlockerService(BlockerRepository blockerRepository) {
        this.blockerRepository = blockerRepository;
    }

    /**
     * Record a new blocker for a region.
     */
    @Transactional
    public BlockerEntity recordBlocker(LocalDate businessDate, String region, BlockerType type, String reference) {
        BlockerEntity entity = new BlockerEntity(
                UUID.randomUUID().toString(), businessDate, region, type, reference);
        return blockerRepository.save(entity);
    }

    /**
     * Get all unresolved blockers for a region on the given business date.
     */
    @Transactional(readOnly = true)
    public List<BlockerEntity> getOpenBlockers(LocalDate businessDate, String region) {
        return blockerRepository.findByBusinessDateAndRegionCodeAndResolvedFalse(businessDate, region);
    }

    /**
     * Get all blockers (resolved and unresolved) for a region on the given business date.
     */
    @Transactional(readOnly = true)
    public List<BlockerEntity> getAllBlockers(LocalDate businessDate, String region) {
        return blockerRepository.findByBusinessDateAndRegionCode(businessDate, region);
    }

    /**
     * Convert open blocker entities to domain Blocker objects for the readiness evaluator.
     */
    public List<Blocker> toOpenDomainBlockers(LocalDate businessDate, String region) {
        return getOpenBlockers(businessDate, region).stream()
                .map(e -> Blocker.of(e.getBlockerType(), e.getReference() != null ? e.getReference() : ""))
                .toList();
    }
}
