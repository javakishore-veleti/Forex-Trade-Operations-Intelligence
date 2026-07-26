package com.fxtradeops.tradelifecycle.api.dto;

import java.util.List;

/**
 * View showing expected lifecycle steps and their reached/pending state.
 */
public record ExpectedLifecycleView(
        String tradeId,
        List<LifecycleStepView> steps
) {

    public record LifecycleStepView(
            String status,
            boolean reached
    ) {
    }
}
