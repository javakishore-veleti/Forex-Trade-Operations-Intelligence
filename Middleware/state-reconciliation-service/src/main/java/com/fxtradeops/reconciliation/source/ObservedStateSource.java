package com.fxtradeops.reconciliation.source;

import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;

/**
 * Port for reading observed trade state from a system of record.
 * Implementations are read-only and never throw to the caller — they return UNAVAILABLE on failure.
 */
public interface ObservedStateSource {

    /**
     * The source identifier.
     */
    SourceId sourceId();

    /**
     * Reads the observed state for the given trade.
     * Returns UNAVAILABLE if the source cannot be read (never throws).
     */
    ObservedState read(String tradeId);
}
