/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared CBOR {@link ObjectMapper} used by all DLQ client classes.
 *
 * <p>The DLQ topics ({@code dlq}, {@code dlq.replay}, {@code dlq.replay-results}) carry CBOR
 * payloads serialised by the engine via Quarkus's {@code ObjectMapperSerde}, which is backed by the
 * CDI-produced {@code ObjectMapper} configured with {@code CBORFactory}. This mapper must use the
 * same {@code CBORFactory} so that client-side serialisation and deserialisation are
 * wire-compatible with the engine.
 *
 * <p>The DLQ DTOs use field-named CBOR (no {@code @JsonFormat(shape = ARRAY)}), matching the engine
 * serialisation.
 */
final class DlqClientMapper {

  static final ObjectMapper INSTANCE =
      new ObjectMapper(new CBORFactory())
          .registerModule(new JavaTimeModule())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private DlqClientMapper() {}
}
