package com.fxtradeops.riskcalc.consumer;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.riskcalc.application.AggregationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Listens for TRADE_CANCELLED and TRADE_AMENDED events on fxops.trade.events
 * to reverse aggregation contributions.
 */
@Component
public class TradeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);

    private final AggregationService aggregationService;

    public TradeEventConsumer(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @KafkaListener(topics = "fxops.trade.events", groupId = "risk-calculation-group")
    public void onTradeEvent(ConsumerRecord<String, TradeEvent> record,
                             Acknowledgment acknowledgment) {
        TradeEvent event = record.value();
        MDC.put("correlationId", event.correlationId());

        try {
            if (event.eventType() == TradeEventType.TRADE_CANCELLED ||
                    event.eventType() == TradeEventType.TRADE_AMENDED) {

                String tradeId = event.tradeId();
                log.info("Processing {} for tradeId={}", event.eventType(), tradeId);

                aggregationService.reverseContribution(tradeId);

                log.info("Aggregation reversed for tradeId={} due to {}", tradeId, event.eventType());
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing trade event: {}", e.getMessage(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
