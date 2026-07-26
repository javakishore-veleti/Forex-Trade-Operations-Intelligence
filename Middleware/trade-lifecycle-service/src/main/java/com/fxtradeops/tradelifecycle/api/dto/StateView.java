package com.fxtradeops.tradelifecycle.api.dto;

import java.time.Instant;

/**
 * Read-only view of a trade's current lifecycle state.
 */
public record StateView(
        String tradeId,
        String status,
        Instant updatedAt,
        Long version
) {
}
