package com.fxtradeops.reconciliation.domain.invariant;

/**
 * Stable invariant codes for cross-source business rules.
 */
public enum Invariant {

    INV_SETTLED_NOT_PENDING_IN_CACHE(
            "INV_SETTLED_NOT_PENDING_IN_CACHE",
            "A SETTLED trade must not appear PENDING in the CACHE"
    ),
    INV_CANCELLED_NOT_ADVANCING(
            "INV_CANCELLED_NOT_ADVANCING",
            "A CANCELLED canonical must not show a post-cancel status anywhere"
    ),
    INV_NO_SOURCE_AHEAD_OF_CANONICAL(
            "INV_NO_SOURCE_AHEAD_OF_CANONICAL",
            "No source may be AHEAD of a terminal canonical state"
    ),
    INV_HISTORY_COMPLETE(
            "INV_HISTORY_COMPLETE",
            "Derivation must not be INCOMPLETE_HISTORY for a terminal trade"
    );

    private final String code;
    private final String description;

    Invariant(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }
}
