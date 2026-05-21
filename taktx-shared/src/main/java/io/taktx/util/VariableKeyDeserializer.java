/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.VariableKeyDTO;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class VariableKeyDeserializer implements Deserializer<VariableKeyDTO> {

  @Override
  public VariableKeyDTO deserialize(String topic, byte[] data) {
    return data == null ? null : fromBytes(data);
  }

  public static VariableKeyDTO fromBytes(byte[] data) {
    if (data == null) {
      throw new SerializationException("VariableKeyDTO deserialization failed: key bytes are null");
    }

    int flowNodeKeyLength = FlowNodeInstanceKeyDeserializer.serializedLength(data, 0);
    int minimumLength = flowNodeKeyLength + VariableKeySerializer.STRING_LENGTH_BYTES;
    if (data.length < minimumLength) {
      throw new SerializationException(
          "VariableKeyDTO deserialization failed: expected at least "
              + minimumLength
              + " bytes but got "
              + data.length);
    }

    ByteBuffer buffer = ByteBuffer.wrap(data, flowNodeKeyLength, data.length - flowNodeKeyLength);
    int variableNameLength = Short.toUnsignedInt(buffer.getShort());
    int expectedLength = minimumLength + variableNameLength;
    if (data.length != expectedLength) {
      throw new SerializationException(
          "VariableKeyDTO deserialization failed: expected "
              + expectedLength
              + " bytes but got "
              + data.length);
    }

    FlowNodeInstanceKeyDTO flowNodeInstanceKey =
        FlowNodeInstanceKeyDeserializer.fromBytes(Arrays.copyOfRange(data, 0, flowNodeKeyLength));
    String variableName =
        new String(Arrays.copyOfRange(data, minimumLength, expectedLength), StandardCharsets.UTF_8);
    return new VariableKeyDTO(flowNodeInstanceKey, variableName);
  }
}
