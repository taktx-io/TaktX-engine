/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import io.taktx.dto.DlqEnvelope;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;

/** CBOR deserializer for {@link DlqEnvelope} records read from the {@code dlq} topic. */
public class DlqEnvelopeCborDeserializer implements Deserializer<DlqEnvelope> {

  @Override
  public DlqEnvelope deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return DlqClientMapper.INSTANCE.readValue(data, DlqEnvelope.class);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to deserialise DlqEnvelope from topic=" + topic + ": " + e.getMessage(), e);
    }
  }
}
