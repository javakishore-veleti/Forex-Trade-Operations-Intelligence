package com.fxtradeops.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxtradeops.domain.config.DomainObjectMapper;

/**
 * Shared test utility providing a correctly configured ObjectMapper for all round-trip tests.
 */
public final class TestObjectMapperProvider {

    private static final ObjectMapper MAPPER = DomainObjectMapper.create();

    private TestObjectMapperProvider() {
        // utility class
    }

    public static ObjectMapper objectMapper() {
        return MAPPER;
    }
}
