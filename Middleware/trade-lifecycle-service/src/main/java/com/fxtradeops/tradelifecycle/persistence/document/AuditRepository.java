package com.fxtradeops.tradelifecycle.persistence.document;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Append-only MongoDB repository for lifecycle audit entries.
 * Only insert operations are used — no update or delete.
 */
@Repository
public interface AuditRepository extends MongoRepository<AuditEntryDocument, String> {

    /**
     * Find all audit entries for a trade, ordered by occurredAt then recordedAt.
     */
    List<AuditEntryDocument> findByTradeIdOrderByOccurredAtAscRecordedAtAsc(String tradeId);

    /**
     * Check if any audit entries exist for a trade.
     */
    boolean existsByTradeId(String tradeId);
}
