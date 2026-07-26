package com.fxtradeops.eod.persistence;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Dedup marker for consumed readiness signals (GP-Rq-5.3).
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "processed_at")
    private Instant processedAt;

    public ProcessedEventEntity() {
    }

    public ProcessedEventEntity(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
