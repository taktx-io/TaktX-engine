/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import io.taktx.dto.FlowNodeInstanceKeyDTO;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class FlowNodeInstanceKeySerde implements Serde<FlowNodeInstanceKeyDTO> {

  private final Serializer<FlowNodeInstanceKeyDTO> serializer = new FlowNodeInstanceKeySerializer();
  private final Deserializer<FlowNodeInstanceKeyDTO> deserializer =
      new FlowNodeInstanceKeyDeserializer();

  @Override
  public Serializer<FlowNodeInstanceKeyDTO> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<FlowNodeInstanceKeyDTO> deserializer() {
    return deserializer;
  }
}
