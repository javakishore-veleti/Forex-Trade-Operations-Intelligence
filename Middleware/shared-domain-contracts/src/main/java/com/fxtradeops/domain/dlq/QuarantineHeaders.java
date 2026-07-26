package com.fxtradeops.domain.dlq;

/**
 * Standard header keys attached to every dead-lettered message.
 * These headers enable triage operators to diagnose failures without reproducing them.
 */
public final class QuarantineHeaders {

    private QuarantineHeaders() {
        // constants class — no instantiation
    }

    /** The original topic from which the message was consumed. */
    public static final String ORIGIN_TOPIC = "dlq.origin.topic";

    /** The partition on the origin topic from which the message was consumed. */
    public static final String ORIGIN_PARTITION = "dlq.origin.partition";

    /** The offset on the origin topic from which the message was consumed. */
    public static final String ORIGIN_OFFSET = "dlq.origin.offset";

    /** The exception class name and message (truncated to 500 chars). */
    public static final String FAILURE_REASON = "dlq.failure.reason";

    /** Total number of attempts (including retries) before dead-lettering. */
    public static final String FAILURE_COUNT = "dlq.failure.count";

    /** ISO-8601 instant when the final failure occurred. */
    public static final String FAILURE_TIMESTAMP = "dlq.failure.timestamp";

    /** Boolean flag indicating whether the message is a poison message. */
    public static final String POISON_FLAG = "dlq.poison.flag";

    /** The correlationId propagated from the original message envelope. */
    public static final String CORRELATION_ID = "dlq.correlation.id";

    /** Header set on replayed messages — contains the human-approval reference. */
    public static final String REPLAY_APPROVAL = "dlq.replay.approval";

    /** Maximum length for the failure reason header value. */
    public static final int FAILURE_REASON_MAX_LENGTH = 500;
}
