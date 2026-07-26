package com.fxtradeops.riskcalc.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity for the eod_risk_snapshots table — EOD finalized totals.
 */
@Entity
@Table(name = "eod_risk_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scope_type", "scope_id", "business_date"}))
public class EodSnapshotEntity {

    @Id
    @Column(name = "snapshot_id", length = 36)
    private String snapshotId;

    @Column(name = "scope_type", length = 8, nullable = false)
    private String scopeType;

    @Column(name = "scope_id", length = 50, nullable = false)
    private String scopeId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "total_risk_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalRiskAmount;

    @Column(name = "trade_count", nullable = false)
    private int tradeCount;

    @Column(name = "rule_version", length = 20)
    private String ruleVersion;

    @Column(name = "snapshotted_at", nullable = false)
    private Instant snapshottedAt;

    public EodSnapshotEntity() {
    }

    // Getters and setters
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public BigDecimal getTotalRiskAmount() { return totalRiskAmount; }
    public void setTotalRiskAmount(BigDecimal totalRiskAmount) { this.totalRiskAmount = totalRiskAmount; }

    public int getTradeCount() { return tradeCount; }
    public void setTradeCount(int tradeCount) { this.tradeCount = tradeCount; }

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }

    public Instant getSnapshottedAt() { return snapshottedAt; }
    public void setSnapshottedAt(Instant snapshottedAt) { this.snapshottedAt = snapshottedAt; }
}
