package com.fxtradeops.eod.api.dto;

import java.time.Instant;

/**
 * View of a blocker for a region.
 */
public record BlockerView(
        String blockerId,
        String blockerType,
        String reference,
        boolean resolved,
        String approvalReference,
        Instant detectedAt,
        Instant resolvedAt
) {
}
