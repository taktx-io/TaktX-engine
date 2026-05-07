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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared plain-JSON {@link ObjectMapper} used by all DLQ client classes.
 *
 * <p>The DLQ topics ({@code dlq}, {@code dlq.replay}, {@code dlq.replay-results}) carry JSON
 * payloads serialised by the engine via Quarkus's {@code ObjectMapperSerde}. This mapper must be
 * JSON-only (no CBORFactory) to match that encoding.
 */
final class DlqClientMapper {

  static final ObjectMapper INSTANCE =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private DlqClientMapper() {}
}
