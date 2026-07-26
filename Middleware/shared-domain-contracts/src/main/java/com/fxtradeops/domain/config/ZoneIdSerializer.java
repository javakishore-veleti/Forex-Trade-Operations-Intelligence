package com.fxtradeops.domain.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.ZoneId;

/**
 * Serializes a {@link ZoneId} as its IANA zone name string (e.g. "Asia/Singapore").
 */
public final class ZoneIdSerializer extends JsonSerializer<ZoneId> {

    @Override
    public void serialize(ZoneId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.getId());
    }
}
