package com.fxtradeops.riskcalc.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed event deduplication service.
 * SET dedup:risk:{eventId} with TTL to detect already-processed events.
 */
@Service
public class DedupService {

    private static final String KEY_PREFIX = "dedup:risk:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public DedupService(StringRedisTemplate redisTemplate,
                        @Value("${risk.dedup.ttl-seconds:86400}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Returns true if this eventId has already been seen (duplicate).
     */
    public boolean seen(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + eventId));
    }

    /**
     * Mark an eventId as processed.
     */
    public void mark(String eventId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + eventId, "1", ttl);
    }
}
