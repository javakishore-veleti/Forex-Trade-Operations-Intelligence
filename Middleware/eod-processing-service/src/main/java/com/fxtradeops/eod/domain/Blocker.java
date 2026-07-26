package com.fxtradeops.eod.domain;

/**
 * An unresolved condition that prevents a region from reaching READY.
 */
public record Blocker(BlockerType type, String detail) {

    public static Blocker of(BlockerType type, String detail) {
        return new Blocker(type, detail);
    }

    public static Blocker of(BlockerType type, int count) {
        return new Blocker(type, String.valueOf(count));
    }
}
