package com.fxtradeops.tradeingest.api.dto;

import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.trade.CurrencyPair;
import com.fxtradeops.domain.trade.TradeDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound DTO for trade capture requests with Bean Validation annotations.
 */
public record TradeRequest(
        @NotNull @Valid CurrencyPair currencyPair,
        @NotNull @Positive BigDecimal notionalAmount,
        @NotBlank @Size(min = 3, max = 3) String notionalCurrency,
        @NotNull TradeDirection direction,
        @NotNull LocalDate tradeDate,
        @NotNull LocalDate valueDate,
        @NotBlank String counterpartyId,
        @NotBlank String tradingBookId,
        @NotNull RegionCode regionCode
) {
}
