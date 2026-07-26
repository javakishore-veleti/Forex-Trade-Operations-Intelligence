package com.fxtradeops.calendar.domain;

import java.time.LocalTime;

/**
 * The local time-of-day after which a region no longer accepts trades for the current business date.
 * Stored as LocalTime (never a fixed UTC offset) to remain DST-safe.
 */
public record Cutoff(LocalTime localTime) {

    public Cutoff {
        if (localTime == null) {
            throw new IllegalArgumentException("Cutoff local time must not be null");
        }
    }
}
