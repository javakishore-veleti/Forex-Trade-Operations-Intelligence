package com.fxtradeops.domain.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.ZoneId;

/**
 * Deserializes a ZoneId from its IANA zone name string (e.g. "Asia/Singapore").
 */
public final class ZoneIdDeserializer extends JsonDeserializer<ZoneId> {

    @Override
    public ZoneId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return ZoneId.of(p.getText());
    }
}
