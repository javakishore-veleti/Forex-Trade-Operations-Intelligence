package com.fxtradeops.reconciliation.domain.impact;

/**
 * Deterministic ordinal business-impact severity scale.
 * NONE < LOW < MEDIUM < HIGH < CRITICAL
 */
public enum BusinessImpact {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
