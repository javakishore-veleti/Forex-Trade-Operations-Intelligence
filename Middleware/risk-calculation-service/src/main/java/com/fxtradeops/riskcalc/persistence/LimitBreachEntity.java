package com.fxtradeops.riskcalc.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for the limit_breaches table — append-only breach facts.
 */
@Entity
@Table(name = "limit_breaches")
public class LimitBreachEntity {

    @Id
    @Column(name = "breach_id", length = 36)
    private String breachId;

    @Column(name = "calculation_id", length = 36, nullable = false)
    private String calculationId;

    @Column(name = "scope_type", length = 12, nullable = false)
    private String scopeType;

    @Column(name = "scope_id", length = 50, nullable = false)
    private String scopeId;

    @Column(name = "limit_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal limitAmount;

    @Column(name = "observed_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal observedAmount;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    public LimitBreachEntity() {
    }

    // Getters and setters
    public String getBreachId() { return breachId; }
    public void setBreachId(String breachId) { this.breachId = breachId; }

    public String getCalculationId() { return calculationId; }
    public void setCalculationId(String calculationId) { this.calculationId = calculationId; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }

    public BigDecimal getLimitAmount() { return limitAmount; }
    public void setLimitAmount(BigDecimal limitAmount) { this.limitAmount = limitAmount; }

    public BigDecimal getObservedAmount() { return observedAmount; }
    public void setObservedAmount(BigDecimal observedAmount) { this.observedAmount = observedAmount; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
}
