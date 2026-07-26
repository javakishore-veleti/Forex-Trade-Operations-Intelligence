package com.fxtradeops.tradelifecycle.persistence.relational;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Relational fallback for event deduplication when Redis is unavailable.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
