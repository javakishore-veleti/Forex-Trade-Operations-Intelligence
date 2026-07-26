package com.fxtradeops.tradelifecycle.application;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.tradelifecycle.domain.StateMachine;
import com.fxtradeops.tradelifecycle.persistence.document.AuditEntryDocument;
import com.fxtradeops.tradelifecycle.persistence.document.AuditRepository;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateEntity;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Orchestrates lifecycle transitions: validates via state machine, persists state + audit atomically.
 */
@Service
public class LifecycleService {

    private static final Logger log = LoggerFactory.getLogger(LifecycleService.class);

    private final TradeCurrentStateRepository stateRepository;
    private final AuditRepository auditRepository;
    private final MeterRegistry meterRegistry;

    public LifecycleService(TradeCurrentStateRepository stateRepository,
                            AuditRepository auditRepository,
                            MeterRegistry meterRegistry) {
        this.stateRepository = stateRepository;
        this.auditRepository = auditRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Process a trade event: resolve target status, decide transition, persist state + audit.
     * This method must be called within a transaction that encompasses both Postgres and the Mongo write.
     */
    @Transactional
    public void process(TradeEvent event) {
        Optional<TradeStatus> targetOpt = StateMachine.targetFor(event.eventType());
        if (targetOpt.isEmpty()) {
            log.debug("Event type {} has no status mapping, skipping", event.eventType());
            return;
        }

        TradeStatus targetStatus = targetOpt.get();
        Optional<TradeCurrentStateEntity> existingOpt = stateRepository.findById(event.tradeId());

        if (existingOpt.isEmpty()) {
            handleNewTrade(event, targetStatus);
        } else {
            handleExistingTrade(event, existingOpt.get(), targetStatus);
        }
    }

    private void handleNewTrade(TradeEvent event, TradeStatus targetStatus) {
        if (event.eventType() == TradeEventType.TRADE_CAPTURED) {
            // Initialize new aggregate
            TradeCurrentStateEntity entity = new TradeCurrentStateEntity(
                    event.tradeId(), TradeStatus.CAPTURED, event.correlationId(), Instant.now());
            stateRepository.save(entity);
            appendAudit(event, null, TradeStatus.CAPTURED, false, false, false);
            recordMetric(null, TradeStatus.CAPTURED, false);
            log.info("Trade {} initialized in CAPTURED state", event.tradeId());
        } else {
            // Orphan event — no aggregate creation
            appendAudit(event, null, targetStatus, false, false, true);
            log.warn("Orphan event for unknown trade {}: {}", event.tradeId(), event.eventType());
        }
    }

    private void handleExistingTrade(TradeEvent event, TradeCurrentStateEntity entity, TradeStatus targetStatus) {
        TradeStatus currentStatus = entity.getStatus();

        // Same-status → noop
        if (currentStatus == targetStatus) {
            appendAudit(event, currentStatus, targetStatus, false, true, false);
            log.debug("Noop: trade {} already in status {}", event.tradeId(), currentStatus);
            return;
        }

        // Check if transition is permitted
        if (StateMachine.canTransition(currentStatus, targetStatus)) {
            // Permitted transition
            entity.setStatus(targetStatus);
            entity.setCorrelationId(event.correlationId());
            entity.setUpdatedAt(Instant.now());
            stateRepository.save(entity);
            appendAudit(event, currentStatus, targetStatus, false, false, false);
            recordMetric(currentStatus, targetStatus, false);
            log.info("Trade {} transitioned {} -> {}", event.tradeId(), currentStatus, targetStatus);
        } else {
            // Illegal transition — record rejected, no state change
            appendAudit(event, currentStatus, targetStatus, true, false, false);
            recordMetric(currentStatus, targetStatus, true);
            log.warn("Rejected illegal transition for trade {}: {} -> {}", event.tradeId(), currentStatus, targetStatus);
        }
    }

    private void appendAudit(TradeEvent event, TradeStatus fromStatus, TradeStatus toStatus,
                             boolean rejected, boolean noop, boolean orphan) {
        AuditEntryDocument doc = new AuditEntryDocument();
        doc.setTradeId(event.tradeId());
        doc.setCorrelationId(event.correlationId());
        doc.setEventId(event.eventId());
        doc.setEventType(event.eventType().name());
        doc.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        doc.setToStatus(toStatus != null ? toStatus.name() : null);
        doc.setRejected(rejected);
        doc.setNoop(noop);
        doc.setOrphan(orphan);
        doc.setSourceService(event.sourceService());
        doc.setOccurredAt(event.occurredAt());
        doc.setRecordedAt(Instant.now());
        auditRepository.save(doc);
    }

    private void recordMetric(TradeStatus from, TradeStatus to, boolean rejected) {
        Counter.builder("lifecycle_transitions_total")
                .tag("from", from != null ? from.name() : "NONE")
                .tag("to", to != null ? to.name() : "NONE")
                .tag("rejected", String.valueOf(rejected))
                .register(meterRegistry)
                .increment();
    }
}
