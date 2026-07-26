package com.fxtradeops.domain.event;

import com.fxtradeops.domain.trade.CurrencyPair;
import com.fxtradeops.domain.trade.TradeDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for the TRADE_CAPTURED event — the initial trade capture containing all fields
 * known at ingestion time.
 */
public record TradeCapturedPayload(
        @NotBlank String tradeId,
        @NotNull CurrencyPair currencyPair,
        @NotNull @Positive BigDecimal notionalAmount,
        @NotBlank String notionalCurrency,
        @NotNull TradeDirection direction,
        @NotNull LocalDate tradeDate,
        @NotNull LocalDate valueDate,
        @NotBlank String counterpartyId,
        @NotBlank String tradingBookId,
        @NotBlank String regionCode
) {
}
