package com.fxtradeops.reconciliation.domain.model;

import com.fxtradeops.domain.trade.TradeStatus;

import java.time.Instant;

/**
 * The observed state from a single source for a trade.
 * When available=false, status and sourceTimestamp may be null (UNAVAILABLE).
 */
public record ObservedState(
        SourceId source,
        TradeStatus status,
        Instant sourceTimestamp,
        boolean available
) {

    /**
     * Creates an UNAVAILABLE observed state for a source that could not be read.
     */
    public static ObservedState unavailable(SourceId source) {
        return new ObservedState(source, null, null, false);
    }
}
