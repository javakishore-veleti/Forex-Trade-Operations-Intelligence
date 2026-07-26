package com.fxtradeops.sequenceprocessor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized configuration for the event-sequence-processor.
 */
@ConfigurationProperties(prefix = "fxops.sequence-processor")
public record SequenceProcessorConfig(
        String bootstrapServers,
        String applicationId,
        String inputTopic,
        String outputTopic,
        String stateStoreName,
        Duration gracePeriod
) {
    public SequenceProcessorConfig {
        if (bootstrapServers == null) bootstrapServers = "localhost:9092";
        if (applicationId == null) applicationId = "fxops-event-sequence-processor";
        if (inputTopic == null) inputTopic = "fxops.trade.events";
        if (outputTopic == null) outputTopic = "fxops.sequence.anomalies";
        if (stateStoreName == null) stateStoreName = "sequence-facts-store";
        if (gracePeriod == null) gracePeriod = Duration.ofSeconds(60);
    }
}
