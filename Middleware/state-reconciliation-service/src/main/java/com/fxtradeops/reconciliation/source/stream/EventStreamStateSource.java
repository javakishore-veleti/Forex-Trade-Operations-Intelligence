package com.fxtradeops.reconciliation.source.stream;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.canonical.LifecycleTransitions;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.source.ObservedStateSource;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Read-only adapter for Kafka event stream.
 * Performs a bounded read-only lookup of the latest event for a trade.
 * Does NOT commit offsets or advance domain processing.
 */
@Component
public class EventStreamStateSource implements ObservedStateSource {

    private static final Logger log = LoggerFactory.getLogger(EventStreamStateSource.class);

    private final ConsumerFactory<String, String> consumerFactory;
    private final String topic;

    public EventStreamStateSource(
            ConsumerFactory<String, String> consumerFactory,
            @Value("${reconciliation.kafka.topic:trade-lifecycle-events}") String topic) {
        this.consumerFactory = consumerFactory;
        this.topic = topic;
    }

    @Override
    public SourceId sourceId() {
        return SourceId.EVENT_STREAM;
    }

    @Override
    public ObservedState read(String tradeId) {
        try {
            // Create an ephemeral consumer for bounded read-only lookup
            try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                    "reconciliation-readonly-" + System.nanoTime(), "")) {

                // Get all partitions for topic
                var partitions = consumer.partitionsFor(topic);
                if (partitions == null || partitions.isEmpty()) {
                    return ObservedState.unavailable(SourceId.EVENT_STREAM);
                }

                var topicPartitions = partitions.stream()
                        .map(pi -> new TopicPartition(topic, pi.partition()))
                        .toList();

                consumer.assign(topicPartitions);

                // Seek to end and read backwards for latest event with matching tradeId
                consumer.seekToEnd(topicPartitions);

                String latestEventType = null;
                Instant latestTimestamp = null;

                for (TopicPartition tp : topicPartitions) {
                    long endOffset = consumer.position(tp);
                    long startOffset = Math.max(0, endOffset - 100); // bounded lookup
                    consumer.seek(tp, startOffset);

                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> record : records) {
                        // Key is tradeId in our event schema
                        if (tradeId.equals(record.key())) {
                            // Parse event type from value (simplified JSON parsing)
                            String eventType = extractEventType(record.value());
                            Instant ts = Instant.ofEpochMilli(record.timestamp());
                            if (latestTimestamp == null || ts.isAfter(latestTimestamp)) {
                                latestEventType = eventType;
                                latestTimestamp = ts;
                            }
                        }
                    }
                }

                if (latestEventType == null) {
                    return ObservedState.unavailable(SourceId.EVENT_STREAM);
                }

                // Map event type to induced status
                com.fxtradeops.domain.event.TradeEventType eventType =
                        com.fxtradeops.domain.event.TradeEventType.valueOf(latestEventType);
                TradeStatus inducedStatus = LifecycleTransitions.targetFor(eventType);

                if (inducedStatus == null) {
                    return ObservedState.unavailable(SourceId.EVENT_STREAM);
                }

                return new ObservedState(SourceId.EVENT_STREAM, inducedStatus, latestTimestamp, true);
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to read EVENT_STREAM source for trade {}: {}",
                    MDC.get("correlationId"), tradeId, e.getMessage());
            return ObservedState.unavailable(SourceId.EVENT_STREAM);
        }
    }

    private String extractEventType(String value) {
        // Simple extraction — expects JSON with "eventType":"VALUE"
        if (value == null) return null;
        int idx = value.indexOf("\"eventType\"");
        if (idx < 0) return null;
        int colonIdx = value.indexOf(':', idx);
        int startQuote = value.indexOf('"', colonIdx + 1);
        int endQuote = value.indexOf('"', startQuote + 1);
        if (startQuote < 0 || endQuote < 0) return null;
        return value.substring(startQuote + 1, endQuote);
    }
}
