package com.fxtradeops.domain.event;

/**
 * Categories of domain events emitted during trade processing.
 */
public enum TradeEventType {
    TRADE_CAPTURED,
    TRADE_VALIDATED,
    TRADE_ENRICHED,
    RISK_CALCULATION_REQUESTED,
    RISK_CALCULATION_COMPLETED,
    TRADE_BOOKED,
    TRADE_ALLOCATED,
    TRADE_CONFIRMED,
    TRADE_SETTLED,
    TRADE_CANCELLED,
    TRADE_AMENDED,
    TRADE_FAILED,
    EVENT_REPLAYED,
    PROCESSING_PAUSED,
    PROCESSING_RESUMED
}
