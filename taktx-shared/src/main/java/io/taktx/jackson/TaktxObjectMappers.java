/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

/** Shared ObjectMapper factories for JSON/CBOR payloads that carry {@link Instant} fields. */
public final class TaktxObjectMappers {

  private TaktxObjectMappers() {}

  public static ObjectMapper json() {
    return registerInstantModule(new ObjectMapper());
  }

  public static ObjectMapper cbor() {
    return registerInstantModule(new ObjectMapper(new CBORFactory()));
  }

  private static ObjectMapper registerInstantModule(ObjectMapper objectMapper) {
    SimpleModule instantModule = new SimpleModule();
    instantModule.addSerializer(
        Instant.class,
        new com.fasterxml.jackson.databind.JsonSerializer<>() {
          @Override
          public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
              throws IOException {
            if (value == null) {
              gen.writeNull();
            } else {
              gen.writeString(value.toString());
            }
          }
        });
    instantModule.addDeserializer(Instant.class, new FlexibleInstantDeserializer());
    return objectMapper.registerModule(instantModule);
  }

  static final class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      JsonToken token = parser.currentToken();
      if (token == null) {
        token = parser.nextToken();
      }
      if (token == JsonToken.VALUE_NULL) {
        return null;
      }
      if (token == JsonToken.VALUE_STRING) {
        String text = parser.getText();
        return parseText(text == null ? null : text.trim());
      }
      if (token == JsonToken.VALUE_NUMBER_INT) {
        return fromInteger(parser.getLongValue());
      }
      if (token == JsonToken.VALUE_NUMBER_FLOAT) {
        return fromDecimal(parser.getDecimalValue());
      }
      return (Instant) ctxt.handleUnexpectedToken(Instant.class, parser);
    }

    private static Instant parseText(String text) {
      if (text == null || text.isBlank()) {
        return null;
      }
      try {
        return Instant.parse(text);
      } catch (Exception ignored) {
        try {
          BigDecimal numeric = new BigDecimal(text);
          return fromNumeric(numeric);
        } catch (NumberFormatException numberFormatException) {
          throw new IllegalArgumentException("Unsupported Instant value: " + text, ignored);
        }
      }
    }

    private static Instant fromInteger(long value) {
      if (Math.abs(value) >= 100_000_000_000L) {
        return Instant.ofEpochMilli(value);
      }
      return Instant.ofEpochSecond(value);
    }

    private static Instant fromDecimal(BigDecimal value) {
      return fromNumeric(value.stripTrailingZeros());
    }

    private static Instant fromNumeric(BigDecimal value) {
      long seconds = value.longValue();
      BigDecimal fractional = value.subtract(BigDecimal.valueOf(seconds));
      int nanos = fractional.movePointRight(9).intValue();
      if (nanos < 0) {
        return Instant.ofEpochSecond(seconds, nanos);
      }
      return Instant.ofEpochSecond(seconds, nanos);
    }
  }
}
