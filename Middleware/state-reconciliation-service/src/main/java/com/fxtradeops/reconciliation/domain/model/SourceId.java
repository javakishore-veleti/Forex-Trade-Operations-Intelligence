package com.fxtradeops.reconciliation.domain.model;

/**
 * Identifies the system-of-record source for observed state.
 */
public enum SourceId {
    RELATIONAL,
    DOCUMENT,
    CACHE,
    EVENT_STREAM,
    ANALYTICS_PLATFORM
}
