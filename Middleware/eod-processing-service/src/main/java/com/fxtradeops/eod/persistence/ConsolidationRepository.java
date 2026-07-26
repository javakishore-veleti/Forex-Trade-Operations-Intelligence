package com.fxtradeops.eod.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ConsolidationRepository extends JpaRepository<ConsolidationEntity, LocalDate> {

    Optional<ConsolidationEntity> findByBusinessDate(LocalDate businessDate);
}
