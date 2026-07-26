package com.fxtradeops.eod.consumer;

import com.fxtradeops.eod.application.BlockerService;
import com.fxtradeops.eod.application.DedupService;
import com.fxtradeops.eod.domain.BlockerType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * Kafka consumer for readiness input signals: risk-snapshot + booking-date.
 * Manual ack — only after tx commit (GP-Rq-7.3).
 */
@Component
public class ReadinessSignalConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReadinessSignalConsumer.class);

    private final DedupService dedupService;
    private final BlockerService blockerService;

    public ReadinessSignalConsumer(DedupService dedupService, BlockerService blockerService) {
        this.dedupService = dedupService;
        this.blockerService = blockerService;
    }

    @KafkaListener(topics = "${eod.topics.risk-snapshot:risk-snapshot-completed}",
            groupId = "eod-processing-group")
    @Transactional
    public void consumeRiskSnapshot(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        Map<String, Object> payload = record.value();
        String eventId = (String) payload.getOrDefault("eventId", "");
        String correlationId = (String) payload.getOrDefault("correlationId", "");

        MDC.put("correlationId", correlationId);
        try {
            if (dedupService.isAlreadyProcessed(eventId)) {
                log.debug("Risk snapshot event already processed: {}", eventId);
                ack.acknowledge();
                return;
            }

            log.info("Processing risk snapshot signal: eventId={}", eventId);
            dedupService.markProcessed(eventId);
            ack.acknowledge();
        } finally {
            MDC.remove("correlationId");
        }
    }

    @KafkaListener(topics = "${eod.topics.booking-date:booking-date-classified}",
            groupId = "eod-processing-group")
    @Transactional
    public void consumeBookingDate(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        Map<String, Object> payload = record.value();
        String eventId = (String) payload.getOrDefault("eventId", "");
        String correlationId = (String) payload.getOrDefault("correlationId", "");

        MDC.put("correlationId", correlationId);
        try {
            if (dedupService.isAlreadyProcessed(eventId)) {
                log.debug("Booking date event already processed: {}", eventId);
                ack.acknowledge();
                return;
            }

            // A booking-date classification may indicate a late trade
            String region = (String) payload.getOrDefault("region", "");
            String tradeId = (String) payload.getOrDefault("tradeId", "");
            boolean isLate = Boolean.TRUE.equals(payload.getOrDefault("isLateTrade", false));
            String businessDateStr = (String) payload.getOrDefault("businessDate", "");

            if (isLate && !region.isBlank() && !businessDateStr.isBlank()) {
                LocalDate businessDate = LocalDate.parse(businessDateStr);
                blockerService.recordBlocker(businessDate, region, BlockerType.LATE_TRADE, tradeId);
                log.info("Recorded late trade blocker: region={}, tradeId={}", region, tradeId);
            }

            dedupService.markProcessed(eventId);
            ack.acknowledge();
        } finally {
            MDC.remove("correlationId");
        }
    }
}
