/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.ProcessDefinitionKey;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/** Deserializes process-definition keys from the current binary format and legacy CBOR fallback. */
public class ProcessDefinitionKeyJsonDeserializer implements Deserializer<ProcessDefinitionKey> {

  @Override
  public ProcessDefinitionKey deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }

    try {
      return io.taktx.util.ProcessDefinitionKeyDeserializer.fromBytes(data);
    } catch (RuntimeException binaryFailure) {
      try {
        return LegacyDefinitionKeyCborDecoder.decodeProcessDefinitionKey(data);
      } catch (RuntimeException legacyFailure) {
        throw new SerializationException(
            "Failed to deserialize ProcessDefinitionKey from binary or legacy CBOR key format",
            legacyFailure);
      }
    }
  }
}
