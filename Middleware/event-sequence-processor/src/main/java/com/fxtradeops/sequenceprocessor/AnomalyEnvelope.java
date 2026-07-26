package com.fxtradeops.sequenceprocessor;

import java.time.Instant;
import java.util.Map;

/**
 * Self-contained anomaly output published to fxops.sequence.anomalies.
 * A consumer can understand the violation from this envelope alone.
 */
public record AnomalyEnvelope(
        String tradeId,
        ViolationType violationType,
        Map<String, Object> details,
        Instant detectedAt,
        String correlationId,
        SequenceFact sequenceFactSnapshot
) {
}
