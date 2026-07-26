package com.fxtradeops.riskcalc.consumer;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.risk.RiskCalculationRequest;
import com.fxtradeops.riskcalc.application.DedupService;
import com.fxtradeops.riskcalc.application.RiskCalculationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Kafka consumer for RISK_CALCULATION_REQUESTED events from fxops.risk.requests.
 * Implements dedup → process → ack → mark pattern.
 */
@Component
public class RiskCalculationRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(RiskCalculationRequestedConsumer.class);

    private final DedupService dedupService;
    private final RiskCalculationService riskCalculationService;

    public RiskCalculationRequestedConsumer(DedupService dedupService,
                                           RiskCalculationService riskCalculationService) {
        this.dedupService = dedupService;
        this.riskCalculationService = riskCalculationService;
    }

    @KafkaListener(topics = "fxops.risk.requests", groupId = "risk-calculation-group")
    public void onRiskCalculationRequested(ConsumerRecord<String, TradeEvent> record,
                                           Acknowledgment acknowledgment) {
        TradeEvent event = record.value();

        // Set correlation ID in MDC
        MDC.put("correlationId", event.correlationId());
        try {
            // Only process RISK_CALCULATION_REQUESTED events
            if (event.eventType() != TradeEventType.RISK_CALCULATION_REQUESTED) {
                acknowledgment.acknowledge();
                return;
            }

            String eventId = event.eventId();

            // Dedup check
            if (dedupService.seen(eventId)) {
                log.info("Duplicate event skipped: eventId={}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract trade data from event payload
            Map<String, Object> payload = event.payload();
            String tradeId = event.tradeId();
            String correlationId = event.correlationId();

            String regionCodeStr = getStringOrDefault(payload, "regionCode", "EMEA");
            String tradingBookId = getStringOrDefault(payload, "tradingBookId", "UNKNOWN");
            String currencyPairCode = getStringOrDefault(payload, "currencyPairCode", "UNKNOWN");
            String baseCurrency = getStringOrDefault(payload, "baseCurrency", "USD");
            String quoteCurrency = getStringOrDefault(payload, "quoteCurrency", "EUR");
            String notionalCurrency = getStringOrDefault(payload, "notionalCurrency", baseCurrency);
            BigDecimal notionalAmount = new BigDecimal(
                    getStringOrDefault(payload, "notionalAmount", "1000000"));

            // Validate trade reference data
            RegionCode regionCode;
            try {
                regionCode = RegionCode.valueOf(regionCodeStr);
            } catch (IllegalArgumentException e) {
                riskCalculationService.publishFailedEvent(tradeId, correlationId,
                        "UNRESOLVABLE_REGION_CODE");
                acknowledgment.acknowledge();
                return;
            }

            RiskCalculationRequest request = new RiskCalculationRequest(
                    tradeId, correlationId, eventId, regionCode,
                    tradingBookId, Instant.now(), 1
            );

            // Process
            riskCalculationService.process(request, notionalAmount, notionalCurrency,
                    currencyPairCode, baseCurrency, quoteCurrency);

            // Ack after tx commit
            acknowledgment.acknowledge();

            // Mark as processed in Redis
            dedupService.mark(eventId);

            log.info("Risk calculation completed for event: eventId={}, tradeId={}", eventId, tradeId);
        } catch (Exception e) {
            log.error("Error processing risk calculation request: {}", e.getMessage(), e);
            // Don't ack — message will be redelivered
        } finally {
            MDC.remove("correlationId");
        }
    }

    private String getStringOrDefault(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
