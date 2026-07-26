package com.fxtradeops.eod.domain;

import java.util.List;

/**
 * Result of the pure readiness evaluation — either READY or BLOCKED with unmet conditions.
 */
public record ReadinessResult(RegionalCloseStatus status, List<Blocker> unmet) {

    public static ReadinessResult ready() {
        return new ReadinessResult(RegionalCloseStatus.READY, List.of());
    }

    public static ReadinessResult blocked(List<Blocker> unmet) {
        return new ReadinessResult(RegionalCloseStatus.BLOCKED, unmet);
    }

    public boolean isReady() {
        return status == RegionalCloseStatus.READY;
    }
}
