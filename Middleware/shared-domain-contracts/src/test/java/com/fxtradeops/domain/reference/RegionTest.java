package com.fxtradeops.domain.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegionTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private Region validRegion() {
        return new Region(RegionCode.APAC, "Asia Pacific", ZoneId.of("Asia/Singapore"), "SGD", true);
    }

    @Test
    void validConstruction() {
        Region region = validRegion();
        assertEquals(RegionCode.APAC, region.regionCode());
        assertEquals("Asia Pacific", region.regionName());
        assertEquals(ZoneId.of("Asia/Singapore"), region.timezone());
        assertEquals("SGD", region.baseCurrency());
        assertTrue(region.isActive());

        Set<ConstraintViolation<Region>> violations = validator.validate(region);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullRegionCodeRejected() {
        Region region = new Region(null, "Asia Pacific", ZoneId.of("Asia/Singapore"), "SGD", true);
        Set<ConstraintViolation<Region>> violations = validator.validate(region);
        assertFalse(violations.isEmpty());
    }

    @Test
    void blankRegionNameRejected() {
        Region region = new Region(RegionCode.APAC, "", ZoneId.of("Asia/Singapore"), "SGD", true);
        Set<ConstraintViolation<Region>> violations = validator.validate(region);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullTimezoneRejected() {
        Region region = new Region(RegionCode.APAC, "Asia Pacific", null, "SGD", true);
        Set<ConstraintViolation<Region>> violations = validator.validate(region);
        assertFalse(violations.isEmpty());
    }

    @Test
    void invalidBaseCurrencySizeRejected() {
        Region region = new Region(RegionCode.APAC, "Asia Pacific", ZoneId.of("Asia/Singapore"), "SG", true);
        Set<ConstraintViolation<Region>> violations = validator.validate(region);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        Region original = validRegion();
        String json = mapper.writeValueAsString(original);
        Region deserialized = mapper.readValue(json, Region.class);
        assertEquals(original, deserialized);
    }
}
