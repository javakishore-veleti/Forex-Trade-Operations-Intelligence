package com.fxtradeops.reconciliation.domain.model;

/**
 * Classification of a divergence between observed and canonical state.
 */
public enum DivergenceClassification {
    /** Source is behind the canonical state (earlier on the path). */
    STALE,
    /** Source is ahead of the canonical state (later on the path). */
    AHEAD,
    /** Source is in a status not reachable on the canonical path. */
    CONFLICTING
}
