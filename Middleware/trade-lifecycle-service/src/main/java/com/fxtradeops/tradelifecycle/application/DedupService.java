package com.fxtradeops.tradelifecycle.application;

import com.fxtradeops.tradelifecycle.persistence.cache.ProcessedEventStore;
import com.fxtradeops.tradelifecycle.persistence.relational.ProcessedEventEntity;
import com.fxtradeops.tradelifecycle.persistence.relational.ProcessedEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Deduplication service that checks Redis first, then falls back to the relational store.
 */
@Service
public class DedupService {

    private final ProcessedEventStore redisStore;
    private final ProcessedEventRepository fallbackRepository;

    public DedupService(ProcessedEventStore redisStore, ProcessedEventRepository fallbackRepository) {
        this.redisStore = redisStore;
        this.fallbackRepository = fallbackRepository;
    }

    /**
     * Returns true if the event has already been processed.
     */
    public boolean isDuplicate(String eventId) {
        if (redisStore.seen(eventId)) {
            return true;
        }
        return fallbackRepository.existsById(eventId);
    }

    /**
     * Marks an event as processed in both Redis and the fallback table.
     */
    public void markProcessed(String eventId) {
        redisStore.mark(eventId);
        fallbackRepository.save(new ProcessedEventEntity(eventId, Instant.now()));
    }
}
