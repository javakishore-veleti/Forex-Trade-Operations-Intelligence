package com.fxtradeops.reconciliation.source.cache;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.source.ObservedStateSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Read-only adapter for Redis cached trade state.
 * Key pattern: state:{tradeId}
 */
@Component
public class CacheStateSource implements ObservedStateSource {

    private static final Logger log = LoggerFactory.getLogger(CacheStateSource.class);
    private static final String KEY_PREFIX = "state:";

    private final StringRedisTemplate redisTemplate;

    public CacheStateSource(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public SourceId sourceId() {
        return SourceId.CACHE;
    }

    @Override
    public ObservedState read(String tradeId) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + tradeId);
            if (value == null) {
                return ObservedState.unavailable(SourceId.CACHE);
            }

            // Value format: STATUS or STATUS|timestamp
            String[] parts = value.split("\\|");
            TradeStatus status = TradeStatus.valueOf(parts[0]);
            Instant timestamp = parts.length > 1 ? Instant.parse(parts[1]) : Instant.now();

            return new ObservedState(SourceId.CACHE, status, timestamp, true);
        } catch (Exception e) {
            log.warn("[{}] Failed to read CACHE source for trade {}: {}",
                    MDC.get("correlationId"), tradeId, e.getMessage());
            return ObservedState.unavailable(SourceId.CACHE);
        }
    }
}
