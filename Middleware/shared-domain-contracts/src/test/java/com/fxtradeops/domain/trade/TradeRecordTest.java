package com.fxtradeops.domain.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import com.fxtradeops.domain.reference.RegionCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeRecordTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private TradeRecord validTrade() {
        return new TradeRecord(
                "FX-000001",
                "CORR-000001",
                new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1000000.00"),
                "USD",
                TradeDirection.BUY,
                LocalDate.of(2025, 6, 15),
                LocalDate.of(2025, 6, 17),
                "CP-AURORA-001",
                "BOOK-APAC-001",
                RegionCode.APAC,
                TradeStatus.CAPTURED,
                Instant.parse("2025-06-15T09:00:00Z"),
                Instant.parse("2025-06-15T09:00:00Z"),
                1L
        );
    }

    @Test
    void validConstruction() {
        TradeRecord trade = validTrade();
        assertEquals("FX-000001", trade.tradeId());
        assertEquals("CORR-000001", trade.correlationId());
        assertEquals("USD", trade.currencyPair().baseCurrency());
        assertEquals(new BigDecimal("1000000.00"), trade.notionalAmount());
        assertEquals("USD", trade.notionalCurrency());
        assertEquals(TradeDirection.BUY, trade.direction());
        assertEquals(LocalDate.of(2025, 6, 15), trade.tradeDate());
        assertEquals(LocalDate.of(2025, 6, 17), trade.valueDate());
        assertEquals("CP-AURORA-001", trade.counterpartyId());
        assertEquals("BOOK-APAC-001", trade.tradingBookId());
        assertEquals(RegionCode.APAC, trade.regionCode());
        assertEquals(TradeStatus.CAPTURED, trade.status());
        assertEquals(1L, trade.version());

        Set<ConstraintViolation<TradeRecord>> violations = validator.validate(trade);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullTradeIdRejected() {
        TradeRecord trade = new TradeRecord(
                null, "CORR-000001", new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1000000.00"), "USD", TradeDirection.BUY,
                LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 17),
                "CP-AURORA-001", "BOOK-APAC-001", RegionCode.APAC,
                TradeStatus.CAPTURED, Instant.now(), Instant.now(), 1L);
        Set<ConstraintViolation<TradeRecord>> violations = validator.validate(trade);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullCurrencyPairRejected() {
        TradeRecord trade = new TradeRecord(
                "FX-000001", "CORR-000001", null,
                new BigDecimal("1000000.00"), "USD", TradeDirection.BUY,
                LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 17),
                "CP-AURORA-001", "BOOK-APAC-001", RegionCode.APAC,
                TradeStatus.CAPTURED, Instant.now(), Instant.now(), 1L);
        Set<ConstraintViolation<TradeRecord>> violations = validator.validate(trade);
        assertFalse(violations.isEmpty());
    }

    @Test
    void negativeNotionalAmountRejected() {
        TradeRecord trade = new TradeRecord(
                "FX-000001", "CORR-000001", new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("-100.00"), "USD", TradeDirection.BUY,
                LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 17),
                "CP-AURORA-001", "BOOK-APAC-001", RegionCode.APAC,
                TradeStatus.CAPTURED, Instant.now(), Instant.now(), 1L);
        Set<ConstraintViolation<TradeRecord>> violations = validator.validate(trade);
        assertFalse(violations.isEmpty());
    }

    @Test
    void zeroNotionalAmountRejected() {
        TradeRecord trade = new TradeRecord(
                "FX-000001", "CORR-000001", new CurrencyPair("USD", "INR", "USD/INR"),
                BigDecimal.ZERO, "USD", TradeDirection.BUY,
                LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 17),
                "CP-AURORA-001", "BOOK-APAC-001", RegionCode.APAC,
                TradeStatus.CAPTURED, Instant.now(), Instant.now(), 1L);
        Set<ConstraintViolation<TradeRecord>> violations = validator.validate(trade);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullVersionRejected() {
        TradeRecord trade = new TradeRecord(
                "FX-000001", "CORR-000001", new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1000000.00"), "USD", TradeDirection.BUY,
                LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 17),
                "CP-AURORA-001", "BOOK-APAC-001", RegionCode.APAC,
                TradeStatus.CAPTURED, Instant.now(), Instant.now(), null);
        Set<ConstraintViolation<TradeRecord>> violations = validator.validate(trade);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        TradeRecord original = validTrade();
        String json = mapper.writeValueAsString(original);
        TradeRecord deserialized = mapper.readValue(json, TradeRecord.class);
        assertEquals(original, deserialized);
    }
}
