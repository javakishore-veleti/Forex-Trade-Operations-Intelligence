package com.fxtradeops.eod.application;

import com.fxtradeops.eod.persistence.BranchCompletionEntity;
import com.fxtradeops.eod.persistence.BranchCompletionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Branch completion tracking — idempotent upsert keyed on (businessDate, region, branch).
 */
@Service
public class BranchCompletionService {

    private final BranchCompletionRepository branchCompletionRepository;

    public BranchCompletionService(BranchCompletionRepository branchCompletionRepository) {
        this.branchCompletionRepository = branchCompletionRepository;
    }

    /**
     * Mark a branch as complete. Idempotent — re-marking is a no-op.
     */
    @Transactional
    public BranchCompletionEntity markComplete(LocalDate businessDate, String region, String branchId) {
        return branchCompletionRepository
                .findByBusinessDateAndRegionCodeAndBranchId(businessDate, region, branchId)
                .orElseGet(() -> branchCompletionRepository.save(
                        new BranchCompletionEntity(businessDate, region, branchId)));
    }

    /**
     * Read branch completion status for a region on the given business date.
     */
    @Transactional(readOnly = true)
    public List<BranchCompletionEntity> readBranches(LocalDate businessDate, String region) {
        return branchCompletionRepository.findByBusinessDateAndRegionCode(businessDate, region);
    }

    /**
     * Check if all known branches in a region are complete.
     */
    @Transactional(readOnly = true)
    public boolean allBranchesComplete(LocalDate businessDate, String region) {
        List<BranchCompletionEntity> branches = branchCompletionRepository
                .findByBusinessDateAndRegionCode(businessDate, region);
        return !branches.isEmpty() && branches.stream().allMatch(BranchCompletionEntity::isComplete);
    }

    /**
     * Get incomplete branch IDs as a comma-separated string.
     */
    @Transactional(readOnly = true)
    public String incompleteBranches(LocalDate businessDate, String region) {
        List<BranchCompletionEntity> branches = branchCompletionRepository
                .findByBusinessDateAndRegionCode(businessDate, region);
        return branches.stream()
                .filter(b -> !b.isComplete())
                .map(BranchCompletionEntity::getBranchId)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
