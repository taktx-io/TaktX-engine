/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import io.taktx.dto.VariableKeyDTO;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

public class VariableKeySerializer implements Serializer<VariableKeyDTO> {

  static final int STRING_LENGTH_BYTES = Short.BYTES;

  /**
   * @implSpec Encodes the key as {@code [16B process-instance UUID big-endian | 4B path-element
   *     count big-endian | 8B×n path elements big-endian | 2B variable-name byte length big-endian
   *     | UTF-8 variable-name bytes]}.
   */
  @Override
  public byte[] serialize(String topic, VariableKeyDTO data) {
    return data == null ? null : toBytes(data);
  }

  public static byte[] toBytes(VariableKeyDTO data) {
    if (data == null) {
      throw new SerializationException("VariableKeyDTO serialization failed: key must not be null");
    }
    if (data.getFlowNodeInstanceKey() == null) {
      throw new SerializationException(
          "VariableKeyDTO serialization failed: flowNodeInstanceKey must not be null");
    }
    if (data.getVariableName() == null) {
      throw new SerializationException(
          "VariableKeyDTO serialization failed: variableName must not be null");
    }

    byte[] flowNodeBytes = FlowNodeInstanceKeySerializer.toBytes(data.getFlowNodeInstanceKey());
    byte[] variableNameBytes = data.getVariableName().getBytes(StandardCharsets.UTF_8);
    if (variableNameBytes.length > 0xFFFF) {
      throw new SerializationException(
          "VariableKeyDTO serialization failed: variableName exceeds 65535 UTF-8 bytes");
    }

    ByteBuffer buffer =
        ByteBuffer.allocate(flowNodeBytes.length + STRING_LENGTH_BYTES + variableNameBytes.length);
    buffer.put(flowNodeBytes);
    buffer.putShort((short) variableNameBytes.length);
    buffer.put(variableNameBytes);
    return buffer.array();
  }
}
