package com.fxtradeops.domain.dlq;

import java.time.Duration;

/**
 * Retry policy configuration for event consumption.
 * Values are externalized via application configuration (e.g., application.yml).
 * <p>
 * Default values per topic:
 * <ul>
 *   <li>fxops.trade.events: maxRetries=5, initialBackoff=1s, maxBackoff=30s</li>
 *   <li>fxops.risk.requests: maxRetries=3, initialBackoff=2s, maxBackoff=30s</li>
 * </ul>
 */
public record RetryPolicyConfig(
        int maxRetries,
        Duration initialBackoff,
        Duration maxBackoff
) {
    /** Default retry policy for fxops.trade.events consumers. */
    public static final RetryPolicyConfig TRADE_EVENTS_DEFAULT =
            new RetryPolicyConfig(5, Duration.ofSeconds(1), Duration.ofSeconds(30));

    /** Default retry policy for fxops.risk.requests consumers. */
    public static final RetryPolicyConfig RISK_REQUESTS_DEFAULT =
            new RetryPolicyConfig(3, Duration.ofSeconds(2), Duration.ofSeconds(30));
}
