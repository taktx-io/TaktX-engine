/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
