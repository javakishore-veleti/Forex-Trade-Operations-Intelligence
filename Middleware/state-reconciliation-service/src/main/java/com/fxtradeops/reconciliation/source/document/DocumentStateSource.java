package com.fxtradeops.reconciliation.source.document;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.source.ObservedStateSource;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Read-only adapter for MongoDB trade_lifecycle_audit collection.
 * Reads the latest audit entry's toStatus and occurredAt.
 */
@Component
public class DocumentStateSource implements ObservedStateSource {

    private static final Logger log = LoggerFactory.getLogger(DocumentStateSource.class);
    private static final String COLLECTION = "trade_lifecycle_audit";

    private final MongoTemplate mongoTemplate;

    public DocumentStateSource(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SourceId sourceId() {
        return SourceId.DOCUMENT;
    }

    @Override
    public ObservedState read(String tradeId) {
        try {
            Query query = new Query(Criteria.where("tradeId").is(tradeId))
                    .with(Sort.by(Sort.Direction.DESC, "occurredAt", "sequenceNumber"))
                    .limit(1);

            Document doc = mongoTemplate.findOne(query, Document.class, COLLECTION);
            if (doc == null) {
                return ObservedState.unavailable(SourceId.DOCUMENT);
            }

            String toStatus = doc.getString("toStatus");
            Instant occurredAt = doc.get("occurredAt", java.util.Date.class) != null
                    ? doc.get("occurredAt", java.util.Date.class).toInstant()
                    : null;

            return new ObservedState(
                    SourceId.DOCUMENT,
                    TradeStatus.valueOf(toStatus),
                    occurredAt,
                    true
            );
        } catch (Exception e) {
            log.warn("[{}] Failed to read DOCUMENT source for trade {}: {}",
                    MDC.get("correlationId"), tradeId, e.getMessage());
            return ObservedState.unavailable(SourceId.DOCUMENT);
        }
    }
}
