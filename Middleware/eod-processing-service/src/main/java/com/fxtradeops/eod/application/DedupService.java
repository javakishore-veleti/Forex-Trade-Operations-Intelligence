package com.fxtradeops.eod.application;

import com.fxtradeops.eod.persistence.ProcessedEventEntity;
import com.fxtradeops.eod.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deduplication service for consumed events (GP-Rq-5.3).
 */
@Service
public class DedupService {

    private final ProcessedEventRepository processedEventRepository;

    public DedupService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Check if an event has already been processed.
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    /**
     * Mark an event as processed.
     */
    @Transactional
    public void markProcessed(String eventId) {
        if (!processedEventRepository.existsById(eventId)) {
            processedEventRepository.save(new ProcessedEventEntity(eventId));
        }
    }
}
