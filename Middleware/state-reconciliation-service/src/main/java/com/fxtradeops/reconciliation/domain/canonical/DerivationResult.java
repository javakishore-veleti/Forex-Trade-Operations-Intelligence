package com.fxtradeops.reconciliation.domain.canonical;

import com.fxtradeops.domain.trade.TradeStatus;

/**
 * Result of canonical state derivation.
 * COMPLETE means the full history was successfully folded.
 * INCOMPLETE_HISTORY means the history had a gap or missing required event.
 */
public record DerivationResult(TradeStatus state, DerivationStatus status) {

    public enum DerivationStatus {
        COMPLETE,
        INCOMPLETE_HISTORY
    }

    public static DerivationResult complete(TradeStatus state) {
        return new DerivationResult(state, DerivationStatus.COMPLETE);
    }

    public static DerivationResult incomplete(TradeStatus state) {
        return new DerivationResult(state, DerivationStatus.INCOMPLETE_HISTORY);
    }
}
