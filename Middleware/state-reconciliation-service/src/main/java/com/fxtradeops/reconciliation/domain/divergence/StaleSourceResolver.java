package com.fxtradeops.reconciliation.domain.divergence;

import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Determines the most-likely-stale source deterministically.
 * Uses earliest timestamp with fixed source precedence for tie-breaking.
 * Source precedence (lower = higher priority for "most stale"):
 * CACHE > ANALYTICS_PLATFORM > DOCUMENT > RELATIONAL > EVENT_STREAM
 */
@Component
public class StaleSourceResolver {

    /**
     * Fixed source precedence for tie-breaking.
     * Lower value = more likely to be stale in a tie.
     */
    private static final Map<SourceId, Integer> SOURCE_PRECEDENCE = Map.of(
            SourceId.CACHE, 0,
            SourceId.ANALYTICS_PLATFORM, 1,
            SourceId.DOCUMENT, 2,
            SourceId.RELATIONAL, 3,
            SourceId.EVENT_STREAM, 4
    );

    /**
     * Identifies the most-likely-stale source from divergences.
     * Deterministic: earliest timestamp, then fixed source precedence for ties.
     *
     * @param divergences detected divergences
     * @param states      map of source → observed state (for timestamps)
     * @return the most-likely-stale source, or null if no divergences
     */
    public SourceId resolve(List<Divergence> divergences, Map<SourceId, ObservedState> states) {
        if (divergences == null || divergences.isEmpty()) {
            return null;
        }

        return divergences.stream()
                .map(Divergence::source)
                .min(Comparator
                        .<SourceId, Instant>comparing(
                                sourceId -> {
                                    ObservedState s = states.get(sourceId);
                                    return (s != null && s.sourceTimestamp() != null)
                                            ? s.sourceTimestamp()
                                            : Instant.MAX;
                                })
                        .thenComparing(sourceId -> SOURCE_PRECEDENCE.getOrDefault(sourceId, Integer.MAX_VALUE))
                )
                .orElse(null);
    }
}
