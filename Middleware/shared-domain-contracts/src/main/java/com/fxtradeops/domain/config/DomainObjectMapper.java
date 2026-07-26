package com.fxtradeops.domain.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.ZoneId;

/**
 * Static factory for a correctly configured ObjectMapper shared across all services.
 */
public final class DomainObjectMapper {

    private DomainObjectMapper() {
        // utility class
    }

    /**
     * Creates an ObjectMapper configured for domain serialization:
     * ISO-8601 temporal values, numeric decimals, ZoneId support.
     */
    public static ObjectMapper create() {
        SimpleModule zoneIdModule = new SimpleModule("ZoneIdModule");
        zoneIdModule.addSerializer(ZoneId.class, new ZoneIdSerializer());
        zoneIdModule.addDeserializer(ZoneId.class, new ZoneIdDeserializer());

        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(zoneIdModule)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
