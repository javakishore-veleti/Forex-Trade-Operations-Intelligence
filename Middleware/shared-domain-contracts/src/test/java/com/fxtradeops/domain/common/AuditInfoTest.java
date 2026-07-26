package com.fxtradeops.domain.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.TestObjectMapperProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuditInfoTest {

    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = TestObjectMapperProvider.objectMapper();
    }

    private AuditInfo validAudit() {
        return new AuditInfo(
                Instant.parse("2025-06-15T09:00:00Z"),
                "trade-ingest-service",
                Instant.parse("2025-06-15T09:01:00Z"),
                "trade-ingest-service",
                1L
        );
    }

    @Test
    void validConstruction() {
        AuditInfo audit = validAudit();
        assertEquals(Instant.parse("2025-06-15T09:00:00Z"), audit.createdAt());
        assertEquals("trade-ingest-service", audit.createdBy());
        assertEquals(Instant.parse("2025-06-15T09:01:00Z"), audit.updatedAt());
        assertEquals("trade-ingest-service", audit.updatedBy());
        assertEquals(1L, audit.version());

        Set<ConstraintViolation<AuditInfo>> violations = validator.validate(audit);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullCreatedAtRejected() {
        AuditInfo audit = new AuditInfo(null, "svc", Instant.now(), "svc", 1L);
        Set<ConstraintViolation<AuditInfo>> violations = validator.validate(audit);
        assertFalse(violations.isEmpty());
    }

    @Test
    void blankCreatedByRejected() {
        AuditInfo audit = new AuditInfo(Instant.now(), "", Instant.now(), "svc", 1L);
        Set<ConstraintViolation<AuditInfo>> violations = validator.validate(audit);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullUpdatedAtRejected() {
        AuditInfo audit = new AuditInfo(Instant.now(), "svc", null, "svc", 1L);
        Set<ConstraintViolation<AuditInfo>> violations = validator.validate(audit);
        assertFalse(violations.isEmpty());
    }

    @Test
    void blankUpdatedByRejected() {
        AuditInfo audit = new AuditInfo(Instant.now(), "svc", Instant.now(), "", 1L);
        Set<ConstraintViolation<AuditInfo>> violations = validator.validate(audit);
        assertFalse(violations.isEmpty());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        AuditInfo original = validAudit();
        String json = mapper.writeValueAsString(original);
        AuditInfo deserialized = mapper.readValue(json, AuditInfo.class);
        assertEquals(original, deserialized);
    }
}
