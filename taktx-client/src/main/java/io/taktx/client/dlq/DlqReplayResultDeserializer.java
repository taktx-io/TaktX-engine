/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import io.taktx.dto.DlqReplayResult;
import io.taktx.serdes.DlqReplayResultDtoDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

/** Deserializes protobuf-backed DLQ replay results. */
public class DlqReplayResultDeserializer implements Deserializer<DlqReplayResult> {

  private final DlqReplayResultDtoDeserializer delegate = new DlqReplayResultDtoDeserializer();

  @Override
  public DlqReplayResult deserialize(String topic, byte[] data) {
    return delegate.deserialize(topic, data);
  }
}

