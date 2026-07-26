package com.fxtradeops.domain.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    @Test
    void validConstruction() {
        PageRequest request = new PageRequest(0, 20, "tradeDate", "ASC");
        assertEquals(0, request.page());
        assertEquals(20, request.size());
        assertEquals("tradeDate", request.sortBy());
        assertEquals("ASC", request.sortDirection());

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void negativePageRejected() {
        PageRequest request = new PageRequest(-1, 20, "tradeDate", "ASC");
        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void zeroSizeRejected() {
        PageRequest request = new PageRequest(0, 0, "tradeDate", "ASC");
        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void sizeOver100Rejected() {
        PageRequest request = new PageRequest(0, 101, "tradeDate", "ASC");
        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullSortByAllowed() {
        PageRequest request = new PageRequest(0, 10, null, null);
        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        PageRequest original = new PageRequest(2, 50, "tradeId", "DESC");
        String json = mapper.writeValueAsString(original);
        PageRequest deserialized = mapper.readValue(json, PageRequest.class);
        assertEquals(original, deserialized);
    }
}
