package com.fxtradeops.domain.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradingBookTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private TradingBook validBook() {
        return new TradingBook("BOOK-APAC-001", "APAC Spot Book", RegionCode.APAC, "TRADER-001", true, BookType.SPOT);
    }

    @Test
    void validConstruction() {
        TradingBook book = validBook();
        assertEquals("BOOK-APAC-001", book.bookId());
        assertEquals("APAC Spot Book", book.bookName());
        assertEquals(RegionCode.APAC, book.regionCode());
        assertEquals("TRADER-001", book.traderId());
        assertTrue(book.isActive());
        assertEquals(BookType.SPOT, book.bookType());

        Set<ConstraintViolation<TradingBook>> violations = validator.validate(book);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankBookIdRejected() {
        TradingBook book = new TradingBook("", "APAC Spot Book", RegionCode.APAC, "TRADER-001", true, BookType.SPOT);
        Set<ConstraintViolation<TradingBook>> violations = validator.validate(book);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullRegionCodeRejected() {
        TradingBook book = new TradingBook("BOOK-APAC-001", "APAC Spot Book", null, "TRADER-001", true, BookType.SPOT);
        Set<ConstraintViolation<TradingBook>> violations = validator.validate(book);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullBookTypeRejected() {
        TradingBook book = new TradingBook("BOOK-APAC-001", "APAC Spot Book", RegionCode.APAC, "TRADER-001", true, null);
        Set<ConstraintViolation<TradingBook>> violations = validator.validate(book);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        TradingBook original = validBook();
        String json = mapper.writeValueAsString(original);
        TradingBook deserialized = mapper.readValue(json, TradingBook.class);
        assertEquals(original, deserialized);
    }
}
