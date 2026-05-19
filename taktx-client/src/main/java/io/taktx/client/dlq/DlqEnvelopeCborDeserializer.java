/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import io.taktx.dto.DlqEnvelope;
import io.taktx.serdes.DlqEnvelopeDtoDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

/** Backward-compatible deserializer for protobuf-backed {@link DlqEnvelope} records. */
public class DlqEnvelopeCborDeserializer implements Deserializer<DlqEnvelope> {

  private final DlqEnvelopeDtoDeserializer delegate = new DlqEnvelopeDtoDeserializer();

  @Override
  public DlqEnvelope deserialize(String topic, byte[] data) {
    return delegate.deserialize(topic, data);
  }
}
