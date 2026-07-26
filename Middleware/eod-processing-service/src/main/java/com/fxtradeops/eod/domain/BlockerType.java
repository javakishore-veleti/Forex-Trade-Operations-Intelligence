package com.fxtradeops.eod.domain;

/**
 * Types of blockers that can prevent a region from reaching READY.
 */
public enum BlockerType {
    INCOMPLETE_BRANCH,
    UNPROCESSED_TRADES,
    MISSING_RISK_SNAPSHOT,
    LATE_TRADE
}
