package com.fxtradeops.domain.event;

/**
 * Categories of domain events emitted during trade processing, risk computation,
 * EOD status changes, and replay/reprocessing signals.
 */
public enum TradeEventType {
    // Trade lifecycle events
    TRADE_CAPTURED,
    TRADE_VALIDATED,
    TRADE_ENRICHED,
    TRADE_RISK_CALCULATED,
    TRADE_BOOKED,
    TRADE_ALLOCATED,
    TRADE_CONFIRMED,
    TRADE_SETTLED,
    TRADE_CANCELLED,
    TRADE_AMENDED,
    TRADE_FAILED,

    // Risk events
    RISK_CALCULATION_REQUESTED,
    RISK_CALCULATION_COMPLETED,
    RISK_CALCULATION_FAILED,

    // EOD status events
    REGIONAL_CLOSE_STARTED,
    REGIONAL_CLOSE_READY,
    REGIONAL_CLOSE_BLOCKED,
    REGIONAL_CLOSE_CLOSED,
    GLOBAL_CONSOLIDATION_COMPLETED,

    // Replay / reprocessing
    REPLAY_REQUESTED,
    EVENT_REPLAYED,
    PROCESSING_PAUSED,
    PROCESSING_RESUMED
}
