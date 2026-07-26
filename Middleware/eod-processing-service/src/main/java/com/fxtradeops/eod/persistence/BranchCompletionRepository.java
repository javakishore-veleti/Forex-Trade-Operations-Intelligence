package com.fxtradeops.eod.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchCompletionRepository extends JpaRepository<BranchCompletionEntity, Long> {

    Optional<BranchCompletionEntity> findByBusinessDateAndRegionCodeAndBranchId(
            LocalDate businessDate, String regionCode, String branchId);

    List<BranchCompletionEntity> findByBusinessDateAndRegionCode(LocalDate businessDate, String regionCode);

    long countByBusinessDateAndRegionCodeAndCompleteTrue(LocalDate businessDate, String regionCode);
}
