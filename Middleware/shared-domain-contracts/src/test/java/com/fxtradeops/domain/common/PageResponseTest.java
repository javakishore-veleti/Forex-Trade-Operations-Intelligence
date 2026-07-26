package com.fxtradeops.domain.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.trade.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private TradeRecord sampleTrade() {
        return new TradeRecord(
                "FX-000001", "CORR-000001",
                new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1000000.00"), "USD", TradeDirection.BUY,
                LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 17),
                "CP-AURORA-001", "BOOK-APAC-001", RegionCode.APAC,
                TradeStatus.CAPTURED, Instant.parse("2025-06-15T09:00:00Z"),
                Instant.parse("2025-06-15T09:00:00Z"), 1L);
    }

    @Test
    void validConstruction() {
        PageResponse<TradeRecord> page = new PageResponse<>(
                List.of(sampleTrade()), 0, 20, 1L, 1);
        assertEquals(1, page.content().size());
        assertEquals(0, page.page());
        assertEquals(20, page.size());
        assertEquals(1L, page.totalElements());
        assertEquals(1, page.totalPages());

        Set<ConstraintViolation<PageResponse<TradeRecord>>> violations = validator.validate(page);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullContentRejected() {
        PageResponse<TradeRecord> page = new PageResponse<>(null, 0, 20, 0L, 0);
        Set<ConstraintViolation<PageResponse<TradeRecord>>> violations = validator.validate(page);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        PageResponse<TradeRecord> original = new PageResponse<>(
                List.of(sampleTrade()), 0, 20, 1L, 1);
        String json = mapper.writeValueAsString(original);
        PageResponse<TradeRecord> deserialized = mapper.readValue(json,
                new TypeReference<PageResponse<TradeRecord>>() {});
        assertEquals(original.content().size(), deserialized.content().size());
        assertEquals(original.content().get(0), deserialized.content().get(0));
        assertEquals(original.page(), deserialized.page());
        assertEquals(original.size(), deserialized.size());
        assertEquals(original.totalElements(), deserialized.totalElements());
        assertEquals(original.totalPages(), deserialized.totalPages());
    }
}
