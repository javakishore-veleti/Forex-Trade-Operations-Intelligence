package com.fxtradeops.riskcalc.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * JPA entity for the risk_limits table — configured maximum permitted risk amounts.
 */
@Entity
@Table(name = "risk_limits")
public class LimitEntity {

    @Id
    @Column(name = "limit_id", length = 36)
    private String limitId;

    @Column(name = "scope_type", length = 12, nullable = false)
    private String scopeType;

    @Column(name = "scope_id", length = 50, nullable = false)
    private String scopeId;

    @Column(name = "limit_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal limitAmount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    public LimitEntity() {
    }

    // Getters and setters
    public String getLimitId() { return limitId; }
    public void setLimitId(String limitId) { this.limitId = limitId; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }

    public BigDecimal getLimitAmount() { return limitAmount; }
    public void setLimitAmount(BigDecimal limitAmount) { this.limitAmount = limitAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
