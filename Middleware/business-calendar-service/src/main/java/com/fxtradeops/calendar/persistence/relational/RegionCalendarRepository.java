package com.fxtradeops.calendar.persistence.relational;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionCalendarRepository extends JpaRepository<RegionCalendarEntity, String> {
}
