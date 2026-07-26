package com.fxtradeops.eod.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegionalCloseRepository extends JpaRepository<RegionalCloseEntity, Long> {

    Optional<RegionalCloseEntity> findByBusinessDateAndRegionCode(LocalDate businessDate, String regionCode);

    List<RegionalCloseEntity> findByBusinessDate(LocalDate businessDate);
}
