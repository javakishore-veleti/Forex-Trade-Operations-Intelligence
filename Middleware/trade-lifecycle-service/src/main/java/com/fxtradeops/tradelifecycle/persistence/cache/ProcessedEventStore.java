package com.fxtradeops.tradelifecycle.persistence.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-based dedup store for processed event IDs.
 * Uses SET with TTL to track which events have already been processed.
 */
@Component
public class ProcessedEventStore {

    private static final String KEY_PREFIX = "processed:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public ProcessedEventStore(StringRedisTemplate redisTemplate,
                               @Value("${lifecycle.dedup.ttl-hours:24}") int ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Returns true if this eventId has already been processed.
     */
    public boolean seen(String eventId) {
        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + eventId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            // Redis unavailable — fall through to allow processing (fallback table will catch dups)
            return false;
        }
    }

    /**
     * Marks an eventId as processed with TTL.
     */
    public void mark(String eventId) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + eventId, "1", ttl);
        } catch (Exception e) {
            // Redis unavailable — dedup will rely on the relational fallback
        }
    }
}
