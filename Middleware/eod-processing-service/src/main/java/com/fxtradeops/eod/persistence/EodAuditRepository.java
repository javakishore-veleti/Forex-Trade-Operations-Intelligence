package com.fxtradeops.eod.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Append-only repository — insert only. No update/delete operations should be exposed.
 */
@Repository
public interface EodAuditRepository extends JpaRepository<EodAuditEntity, String> {
}
