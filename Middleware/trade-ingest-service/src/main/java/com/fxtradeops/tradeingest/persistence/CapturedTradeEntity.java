package com.fxtradeops.tradeingest.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a captured trade persisted in the captured_trades table.
 */
@Entity
@Table(name = "captured_trades")
public class CapturedTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, unique = true, length = 10)
    private String tradeId;

    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @Column(name = "currency_pair_code", nullable = false, length = 7)
    private String currencyPairCode;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(name = "notional_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal notionalAmount;

    @Column(name = "notional_currency", nullable = false, length = 3)
    private String notionalCurrency;

    @Column(name = "direction", nullable = false, length = 4)
    private String direction;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "counterparty_id", nullable = false, length = 50)
    private String counterpartyId;

    @Column(name = "trading_book_id", nullable = false, length = 50)
    private String tradingBookId;

    @Column(name = "region_code", nullable = false, length = 8)
    private String regionCode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public CapturedTradeEntity() {
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCurrencyPairCode() {
        return currencyPairCode;
    }

    public void setCurrencyPairCode(String currencyPairCode) {
        this.currencyPairCode = currencyPairCode;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    public BigDecimal getNotionalAmount() {
        return notionalAmount;
    }

    public void setNotionalAmount(BigDecimal notionalAmount) {
        this.notionalAmount = notionalAmount;
    }

    public String getNotionalCurrency() {
        return notionalCurrency;
    }

    public void setNotionalCurrency(String notionalCurrency) {
        this.notionalCurrency = notionalCurrency;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public String getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(String counterpartyId) {
        this.counterpartyId = counterpartyId;
    }

    public String getTradingBookId() {
        return tradingBookId;
    }

    public void setTradingBookId(String tradingBookId) {
        this.tradingBookId = tradingBookId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
