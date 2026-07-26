package com.fxtradeops.domain.trade;

/**
 * Lifecycle states of a foreign-exchange trade.
 */
public enum TradeStatus {
    CAPTURED,
    VALIDATED,
    ENRICHED,
    RISK_CALCULATED,
    BOOKED,
    ALLOCATED,
    CONFIRMED,
    SETTLED,
    CANCELLED,
    AMENDED,
    FAILED
}
