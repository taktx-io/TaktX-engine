/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import io.taktx.dto.FlowNodeInstanceKeyDTO;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

public class FlowNodeInstanceKeySerializer implements Serializer<FlowNodeInstanceKeyDTO> {

  /**
   * @implSpec Encodes the key as {@code [16B process-instance UUID big-endian | 4B path-element
   *     count big-endian | 8B×n path elements big-endian]}.
   */
  @Override
  public byte[] serialize(String topic, FlowNodeInstanceKeyDTO data) {
    return data == null ? null : toBytes(data);
  }

  public static byte[] toBytes(FlowNodeInstanceKeyDTO data) {
    if (data == null) {
      throw new SerializationException(
          "FlowNodeInstanceKeyDTO serialization failed: key must not be null");
    }
    if (data.getProcessInstanceId() == null) {
      throw new SerializationException(
          "FlowNodeInstanceKeyDTO serialization failed: processInstanceId must not be null");
    }
    if (data.getFlowNodeInstanceKeyPath() == null) {
      throw new SerializationException(
          "FlowNodeInstanceKeyDTO serialization failed: flowNodeInstanceKeyPath must not be null");
    }

    byte[] processInstanceBytes = TaktUUIDSerializer.toBytes(data.getProcessInstanceId());
    byte[] pathBytes = TaktLongListSerializer.toBytes(data.getFlowNodeInstanceKeyPath());
    byte[] serialized = new byte[processInstanceBytes.length + pathBytes.length];
    System.arraycopy(processInstanceBytes, 0, serialized, 0, processInstanceBytes.length);
    System.arraycopy(pathBytes, 0, serialized, processInstanceBytes.length, pathBytes.length);
    return serialized;
  }
}
