package com.fxtradeops.riskcalc.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable Drools working-memory POJO. Rules set riskAmount, rulesFired, and factor hints.
 */
public class RiskFact {

    private String tradeId;
    private String currencyPairCode;
    private String baseCurrency;
    private String quoteCurrency;
    private BigDecimal notionalAmount;
    private String notionalCurrency;
    private String regionCode;
    private String tradingBookId;

    // Set by rules
    private BigDecimal riskAmount = BigDecimal.ZERO;
    private BigDecimal volatilityFactor = BigDecimal.ZERO;
    private BigDecimal notionalExposureFactor = BigDecimal.ZERO;
    private BigDecimal regionalAdjustmentFactor = BigDecimal.ZERO;
    private List<String> rulesFired = new ArrayList<>();

    public RiskFact() {
    }

    public RiskFact(String tradeId, String currencyPairCode, String baseCurrency,
                    String quoteCurrency, BigDecimal notionalAmount, String notionalCurrency,
                    String regionCode, String tradingBookId) {
        this.tradeId = tradeId;
        this.currencyPairCode = currencyPairCode;
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.notionalAmount = notionalAmount;
        this.notionalCurrency = notionalCurrency;
        this.regionCode = regionCode;
        this.tradingBookId = tradingBookId;
    }

    // Getters and setters
    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getCurrencyPairCode() { return currencyPairCode; }
    public void setCurrencyPairCode(String currencyPairCode) { this.currencyPairCode = currencyPairCode; }

    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }

    public String getQuoteCurrency() { return quoteCurrency; }
    public void setQuoteCurrency(String quoteCurrency) { this.quoteCurrency = quoteCurrency; }

    public BigDecimal getNotionalAmount() { return notionalAmount; }
    public void setNotionalAmount(BigDecimal notionalAmount) { this.notionalAmount = notionalAmount; }

    public String getNotionalCurrency() { return notionalCurrency; }
    public void setNotionalCurrency(String notionalCurrency) { this.notionalCurrency = notionalCurrency; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getTradingBookId() { return tradingBookId; }
    public void setTradingBookId(String tradingBookId) { this.tradingBookId = tradingBookId; }

    public BigDecimal getRiskAmount() { return riskAmount; }
    public void setRiskAmount(BigDecimal riskAmount) { this.riskAmount = riskAmount; }

    public BigDecimal getVolatilityFactor() { return volatilityFactor; }
    public void setVolatilityFactor(BigDecimal volatilityFactor) { this.volatilityFactor = volatilityFactor; }

    public BigDecimal getNotionalExposureFactor() { return notionalExposureFactor; }
    public void setNotionalExposureFactor(BigDecimal notionalExposureFactor) { this.notionalExposureFactor = notionalExposureFactor; }

    public BigDecimal getRegionalAdjustmentFactor() { return regionalAdjustmentFactor; }
    public void setRegionalAdjustmentFactor(BigDecimal regionalAdjustmentFactor) { this.regionalAdjustmentFactor = regionalAdjustmentFactor; }

    public List<String> getRulesFired() { return rulesFired; }
    public void setRulesFired(List<String> rulesFired) { this.rulesFired = rulesFired; }

    public void addRuleFired(String ruleName) {
        this.rulesFired.add(ruleName);
    }
}
