package com.fxtradeops.reconciliation.domain.model;

import com.fxtradeops.domain.trade.TradeStatus;

/**
 * Represents a divergence between a source's observed state and the canonical expected state.
 */
public record Divergence(
        SourceId source,
        TradeStatus observed,
        TradeStatus canonical,
        DivergenceClassification classification
) {
}
