package com.fxtradeops.tradelifecycle.persistence.relational;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the processed_events fallback dedup table.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
}
