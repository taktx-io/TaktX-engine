/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

public class ProcessInstanceTriggerEnvelopeSerializer
    implements Serializer<ProcessInstanceTriggerEnvelope> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  @Override
  public byte[] serialize(String topic, ProcessInstanceTriggerEnvelope data) {
    if (data == null || data.trigger() == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsBytes(data.trigger());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public byte[] serialize(String topic, Headers headers, ProcessInstanceTriggerEnvelope data) {
    return serialize(topic, data);
  }
}
