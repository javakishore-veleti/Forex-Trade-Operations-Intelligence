package com.fxtradeops.tradelifecycle.consumer;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.tradelifecycle.application.DedupService;
import com.fxtradeops.tradelifecycle.application.LifecycleService;
import com.fxtradeops.tradelifecycle.persistence.document.AuditEntryDocument;
import com.fxtradeops.tradelifecycle.persistence.document.AuditRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Kafka consumer for trade domain events.
 * Manual acknowledgment — offset committed only after successful processing.
 */
@Component
public class TradeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    private final DedupService dedupService;
    private final LifecycleService lifecycleService;
    private final AuditRepository auditRepository;

    public TradeEventConsumer(DedupService dedupService,
                              LifecycleService lifecycleService,
                              AuditRepository auditRepository) {
        this.dedupService = dedupService;
        this.lifecycleService = lifecycleService;
        this.auditRepository = auditRepository;
    }

    @KafkaListener(topics = "fxops.trade.events", groupId = "trade-lifecycle-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, TradeEvent> record, Acknowledgment ack) {
        TradeEvent event = record.value();
        if (event == null) {
            ack.acknowledge();
            return;
        }

        // Copy correlation ID to MDC
        MDC.put(CORRELATION_ID_KEY, event.correlationId());

        try {
            log.debug("Received event: {} for trade {}", event.eventType(), event.tradeId());

            // Dedup check
            if (dedupService.isDuplicate(event.eventId())) {
                log.debug("Duplicate event {}, recording noop", event.eventId());
                appendNoopAudit(event);
                ack.acknowledge();
                return;
            }

            // Process the event (state machine + persist)
            lifecycleService.process(event);

            // Mark as processed after successful processing
            dedupService.markProcessed(event.eventId());

            // Ack only after tx commit
            ack.acknowledge();
            log.debug("Successfully processed event {} for trade {}", event.eventId(), event.tradeId());

        } catch (Exception e) {
            // Don't ack — will be redelivered
            log.error("Error processing event {} for trade {}: {}",
                    event.eventId(), event.tradeId(), e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    private void appendNoopAudit(TradeEvent event) {
        AuditEntryDocument doc = new AuditEntryDocument();
        doc.setTradeId(event.tradeId());
        doc.setCorrelationId(event.correlationId());
        doc.setEventId(event.eventId());
        doc.setEventType(event.eventType().name());
        doc.setNoop(true);
        doc.setRejected(false);
        doc.setOrphan(false);
        doc.setSourceService(event.sourceService());
        doc.setOccurredAt(event.occurredAt());
        doc.setRecordedAt(Instant.now());
        auditRepository.save(doc);
    }
}
