package com.fxtradeops.riskcalc.persistence.repositories;

import com.fxtradeops.riskcalc.persistence.EodSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface EodSnapshotRepository extends JpaRepository<EodSnapshotEntity, String> {

    Optional<EodSnapshotEntity> findByScopeTypeAndScopeIdAndBusinessDate(
            String scopeType, String scopeId, LocalDate businessDate);
}
