package com.fxtradeops.sequenceprocessor;

/**
 * Types of sequence violations detected by the event-sequence-processor.
 */
public enum ViolationType {
    /** A required predecessor event was never observed. */
    MISSING_EVENT,

    /** A previously missing event has now been observed within the grace period. */
    MISSING_EVENT_RESOLVED,

    /** An event with the same eventId was observed more than once (identical payload). */
    DUPLICATE_EVENT,

    /** An event with the same eventId was observed twice with differing payload. */
    CONFLICTING_REPLAY,

    /** An event arrived for a status the trade has already surpassed. */
    OUT_OF_ORDER_EVENT
}
