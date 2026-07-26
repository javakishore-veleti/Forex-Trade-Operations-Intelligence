package com.fxtradeops.tradeingest.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing an idempotency key in the idempotency_keys table.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 36)
    private String idempotencyKey;

    @Column(name = "trade_id", nullable = false, length = 10)
    private String tradeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyKeyEntity() {
    }

    public IdempotencyKeyEntity(String idempotencyKey, String tradeId, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.tradeId = tradeId;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
