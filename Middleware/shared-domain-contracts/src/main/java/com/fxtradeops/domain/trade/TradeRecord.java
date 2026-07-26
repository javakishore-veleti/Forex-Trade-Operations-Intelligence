package com.fxtradeops.domain.trade;

import com.fxtradeops.domain.reference.RegionCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable representation of a foreign-exchange trade.
 */
public record TradeRecord(
        @NotBlank String tradeId,
        @NotBlank String correlationId,
        @NotNull @Valid CurrencyPair currencyPair,
        @NotNull @Positive BigDecimal notionalAmount,
        @NotBlank String notionalCurrency,
        @NotNull TradeDirection direction,
        @NotNull LocalDate tradeDate,
        @NotNull LocalDate valueDate,
        @NotBlank String counterpartyId,
        @NotBlank String tradingBookId,
        @NotNull RegionCode regionCode,
        @NotNull TradeStatus status,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull Long version
) {
}
