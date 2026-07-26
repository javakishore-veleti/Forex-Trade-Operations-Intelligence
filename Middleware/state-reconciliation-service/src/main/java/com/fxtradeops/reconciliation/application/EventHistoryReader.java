package com.fxtradeops.reconciliation.application;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Reads the ordered event history for a trade from the DOCUMENT_STORE.
 * Events are ordered by (occurredAt, sequenceNumber) for deterministic derivation.
 * Read-only — never writes.
 */
@Component
public class EventHistoryReader {

    private static final Logger log = LoggerFactory.getLogger(EventHistoryReader.class);
    private static final String COLLECTION = "trade_lifecycle_audit";

    private final MongoTemplate mongoTemplate;

    public EventHistoryReader(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Reads the ordered event history for a trade, suitable for canonical state derivation.
     * Returns empty list on failure (logged, never throws).
     */
    public List<TradeEvent> readOrderedHistory(String tradeId) {
        try {
            Query query = new Query(Criteria.where("tradeId").is(tradeId))
                    .with(Sort.by(Sort.Order.asc("occurredAt"), Sort.Order.asc("sequenceNumber")));

            List<Document> docs = mongoTemplate.find(query, Document.class, COLLECTION);

            return docs.stream()
                    .map(this::toTradeEvent)
                    .toList();
        } catch (Exception e) {
            log.warn("[{}] Failed to read event history for trade {}: {}",
                    MDC.get("correlationId"), tradeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private TradeEvent toTradeEvent(Document doc) {
        return new TradeEvent(
                doc.getString("eventId") != null ? doc.getString("eventId") : "",
                doc.getString("tradeId"),
                doc.getString("correlationId") != null ? doc.getString("correlationId") : "",
                TradeEventType.valueOf(doc.getString("eventType")),
                toInstant(doc.get("occurredAt")),
                doc.get("sequenceNumber") != null ? doc.getLong("sequenceNumber") : 0L,
                doc.getString("sourceService") != null ? doc.getString("sourceService") : "unknown",
                doc.get("payload") != null ? doc.get("payload", Document.class) : Map.of()
        );
    }

    private Instant toInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return Instant.now();
    }
}
