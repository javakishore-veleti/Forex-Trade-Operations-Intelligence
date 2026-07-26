package com.fxtradeops.eod.domain;

import java.util.Map;
import java.util.Set;

/**
 * Fixed regional close lifecycle states with a data-defined legal-transition table.
 */
public enum RegionalCloseStatus {
    IN_PROGRESS,
    BLOCKED,
    READY,
    CLOSED;

    /**
     * Permitted state transitions. Any (from, to) not present is rejected.
     */
    private static final Map<RegionalCloseStatus, Set<RegionalCloseStatus>> PERMITTED = Map.of(
            IN_PROGRESS, Set.of(READY, BLOCKED),
            BLOCKED, Set.of(READY, BLOCKED, IN_PROGRESS),
            READY, Set.of(CLOSED, BLOCKED),
            CLOSED, Set.of()
    );

    public boolean canTransitionTo(RegionalCloseStatus target) {
        return PERMITTED.getOrDefault(this, Set.of()).contains(target);
    }
}
