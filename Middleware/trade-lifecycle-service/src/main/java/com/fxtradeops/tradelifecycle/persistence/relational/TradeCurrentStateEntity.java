package com.fxtradeops.tradelifecycle.persistence.relational;

import com.fxtradeops.domain.trade.TradeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * JPA entity representing the current lifecycle state of a trade.
 * Optimistic locking via @Version.
 */
@Entity
@Table(name = "trade_current_state")
public class TradeCurrentStateEntity {

    @Id
    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TradeStatus status;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected TradeCurrentStateEntity() {
        // JPA
    }

    public TradeCurrentStateEntity(String tradeId, TradeStatus status, String correlationId, Instant updatedAt) {
        this.tradeId = tradeId;
        this.status = status;
        this.correlationId = correlationId;
        this.updatedAt = updatedAt;
    }

    public String getTradeId() {
        return tradeId;
    }

    public TradeStatus getStatus() {
        return status;
    }

    public void setStatus(TradeStatus status) {
        this.status = status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
