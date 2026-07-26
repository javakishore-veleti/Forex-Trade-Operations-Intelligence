package com.fxtradeops.tradeingest.application;

import com.fxtradeops.tradeingest.api.dto.TradeCaptureResponse;
import com.fxtradeops.tradeingest.persistence.IdempotencyKeyEntity;
import com.fxtradeops.tradeingest.persistence.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implements exactly-once idempotency using Redis as primary cache and PostgreSQL as fallback.
 * Redis key: idempotency:{key} with configurable TTL (default 24h).
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            IdempotencyKeyRepository idempotencyKeyRepository,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.ttl-hours:24}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    /**
     * Checks if an idempotency key has already been processed.
     * First checks Redis cache, then falls back to database.
     *
     * @param key the idempotency key
     * @return Optional containing the cached response if key exists
     */
    public Optional<TradeCaptureResponse> check(String key) {
        // Check Redis first
        try {
            String cached = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            if (cached != null) {
                log.debug("Idempotency key found in Redis: {}", key);
                return Optional.of(objectMapper.readValue(cached, TradeCaptureResponse.class));
            }
        } catch (Exception e) {
            log.warn("Redis check failed for key {}, falling back to DB", key, e);
        }

        // Fallback to database
        Optional<IdempotencyKeyEntity> dbEntry = idempotencyKeyRepository.findById(key);
        if (dbEntry.isPresent()) {
            log.debug("Idempotency key found in DB: {}", key);
            TradeCaptureResponse response = new TradeCaptureResponse(
                    dbEntry.get().getTradeId(), null, "CAPTURED");
            // Re-populate Redis cache
            try {
                String json = objectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(KEY_PREFIX + key, json, ttlHours, TimeUnit.HOURS);
            } catch (JsonProcessingException e) {
                log.warn("Failed to re-cache idempotency key in Redis: {}", key, e);
            }
            return Optional.of(response);
        }

        return Optional.empty();
    }

    /**
     * Marks a key as processed in Redis with TTL.
     * Called AFTER successful transaction commit.
     *
     * @param key      the idempotency key
     * @param response the response to cache
     */
    public void mark(String key, TradeCaptureResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + key, json, ttlHours, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response for idempotency marking: {}", key, e);
        }
    }
}
