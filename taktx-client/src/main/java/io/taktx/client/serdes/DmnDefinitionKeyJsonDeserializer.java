/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.jackson.TaktxObjectMappers;
import java.io.IOException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/** Deserializes DMN definition keys from the current protobuf format and legacy CBOR fallback. */
public class DmnDefinitionKeyJsonDeserializer implements Deserializer<DmnDefinitionKey> {

  private final io.taktx.serdes.DmnDefinitionKeyDtoDeserializer delegate =
      new io.taktx.serdes.DmnDefinitionKeyDtoDeserializer();

  @Override
  public DmnDefinitionKey deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }

    try {
      return delegate.deserialize(topic, data);
    } catch (RuntimeException protoFailure) {
      try {
        return TaktxObjectMappers.cbor().readValue(data, DmnDefinitionKey.class);
      } catch (IOException legacyFailure) {
        throw new SerializationException(
            "Failed to deserialize DmnDefinitionKey from protobuf or legacy CBOR key format",
            legacyFailure);
      }
    }
  }
}
