package com.fxtradeops.riskcalc.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for the risk_aggregations table.
 */
@Entity
@Table(name = "risk_aggregations")
@IdClass(RiskAggregationId.class)
public class RiskAggregationEntity {

    @Id
    @Column(name = "scope_type", length = 8, nullable = false)
    private String scopeType;

    @Id
    @Column(name = "scope_id", length = 50, nullable = false)
    private String scopeId;

    @Column(name = "total_risk_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalRiskAmount = BigDecimal.ZERO;

    @Column(name = "risk_currency", length = 3, nullable = false)
    private String riskCurrency;

    @Column(name = "trade_count", nullable = false)
    private int tradeCount = 0;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    public RiskAggregationEntity() {
    }

    // Getters and setters
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }

    public BigDecimal getTotalRiskAmount() { return totalRiskAmount; }
    public void setTotalRiskAmount(BigDecimal totalRiskAmount) { this.totalRiskAmount = totalRiskAmount; }

    public String getRiskCurrency() { return riskCurrency; }
    public void setRiskCurrency(String riskCurrency) { this.riskCurrency = riskCurrency; }

    public int getTradeCount() { return tradeCount; }
    public void setTradeCount(int tradeCount) { this.tradeCount = tradeCount; }

    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
