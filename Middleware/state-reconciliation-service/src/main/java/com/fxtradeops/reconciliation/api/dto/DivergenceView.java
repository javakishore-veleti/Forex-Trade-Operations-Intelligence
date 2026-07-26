package com.fxtradeops.reconciliation.api.dto;

import com.fxtradeops.reconciliation.domain.model.Divergence;

/**
 * JSON view of a divergence.
 */
public record DivergenceView(
        String source,
        String observed,
        String classification
) {

    public static DivergenceView from(Divergence divergence) {
        return new DivergenceView(
                divergence.source().name(),
                divergence.observed().name(),
                divergence.classification().name()
        );
    }
}
