package com.fxtradeops.domain.dlq;

/**
 * Lifecycle states of a dead-lettered message in the quarantine system.
 */
public enum PoisonMessageStatus {
    /** Message is quarantined and awaiting human review. */
    QUARANTINED,

    /** Message has been discarded after human acknowledgment — logged to audit. */
    DISCARDED,

    /** Message has been corrected and reprocessed after human approval. */
    REPROCESSED
}
