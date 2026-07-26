package com.fxtradeops.riskcalc.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for the risk_results table.
 */
@Entity
@Table(name = "risk_results")
public class RiskResultEntity {

    @Id
    @Column(name = "calculation_id", length = 36)
    private String calculationId;

    @Column(name = "trade_id", nullable = false, length = 10)
    private String tradeId;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "risk_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal riskAmount;

    @Column(name = "risk_currency", length = 3, nullable = false)
    private String riskCurrency;

    @Column(name = "region_code", length = 8)
    private String regionCode;

    @Column(name = "trading_book_id", length = 50)
    private String tradingBookId;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "rule_version", length = 20)
    private String ruleVersion;

    @Column(name = "risk_level", length = 8, nullable = false)
    private String riskLevel;

    @Column(name = "rules_fired", columnDefinition = "text")
    private String rulesFired;

    @Column(name = "contributing_factors", columnDefinition = "text")
    private String contributingFactors;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    public RiskResultEntity() {
    }

    // Getters and setters
    public String getCalculationId() { return calculationId; }
    public void setCalculationId(String calculationId) { this.calculationId = calculationId; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public BigDecimal getRiskAmount() { return riskAmount; }
    public void setRiskAmount(BigDecimal riskAmount) { this.riskAmount = riskAmount; }

    public String getRiskCurrency() { return riskCurrency; }
    public void setRiskCurrency(String riskCurrency) { this.riskCurrency = riskCurrency; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getTradingBookId() { return tradingBookId; }
    public void setTradingBookId(String tradingBookId) { this.tradingBookId = tradingBookId; }

    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getRulesFired() { return rulesFired; }
    public void setRulesFired(String rulesFired) { this.rulesFired = rulesFired; }

    public String getContributingFactors() { return contributingFactors; }
    public void setContributingFactors(String contributingFactors) { this.contributingFactors = contributingFactors; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
