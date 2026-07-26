package com.fxtradeops.reconciliation.api.dto;

import com.fxtradeops.reconciliation.domain.model.ObservedState;

/**
 * JSON view of an observed state from a single source.
 */
public record ObservedStateView(
        String status,
        String timestamp,
        boolean available
) {

    public static ObservedStateView from(ObservedState state) {
        return new ObservedStateView(
                state.status() != null ? state.status().name() : null,
                state.sourceTimestamp() != null ? state.sourceTimestamp().toString() : null,
                state.available()
        );
    }
}
