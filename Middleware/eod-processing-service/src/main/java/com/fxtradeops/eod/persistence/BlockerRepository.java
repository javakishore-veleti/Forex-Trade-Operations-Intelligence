package com.fxtradeops.eod.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlockerRepository extends JpaRepository<BlockerEntity, String> {

    List<BlockerEntity> findByBusinessDateAndRegionCodeAndResolvedFalse(LocalDate businessDate, String regionCode);

    List<BlockerEntity> findByBusinessDateAndRegionCode(LocalDate businessDate, String regionCode);

    Optional<BlockerEntity> findByBlockerId(String blockerId);
}
