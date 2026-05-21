/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import io.taktx.dto.FlowNodeInstanceKeyDTO;
import java.util.Arrays;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class FlowNodeInstanceKeyDeserializer implements Deserializer<FlowNodeInstanceKeyDTO> {

  @Override
  public FlowNodeInstanceKeyDTO deserialize(String topic, byte[] data) {
    return data == null ? null : fromBytes(data);
  }

  public static FlowNodeInstanceKeyDTO fromBytes(byte[] data) {
    int serializedLength = serializedLength(data, 0);
    if (data.length != serializedLength) {
      throw new SerializationException(
          "FlowNodeInstanceKeyDTO deserialization failed: expected "
              + serializedLength
              + " bytes but got "
              + data.length);
    }

    return new FlowNodeInstanceKeyDTO(
        TaktUUIDDeserializer.fromBytes(
            Arrays.copyOfRange(data, 0, TaktUUIDSerializer.UUID_BYTE_LENGTH)),
        TaktLongListDeserializer.fromBytes(
            Arrays.copyOfRange(data, TaktUUIDSerializer.UUID_BYTE_LENGTH, data.length)));
  }

  public static int serializedLength(byte[] data, int offset) {
    if (data == null) {
      throw new SerializationException(
          "FlowNodeInstanceKeyDTO deserialization failed: key bytes are null");
    }
    int minimumLength =
        offset + TaktUUIDSerializer.UUID_BYTE_LENGTH + TaktLongListSerializer.COUNT_BYTE_LENGTH;
    if (data.length < minimumLength) {
      throw new SerializationException(
          "FlowNodeInstanceKeyDTO deserialization failed: expected at least "
              + minimumLength
              + " bytes but got "
              + data.length);
    }

    int countOffset = offset + TaktUUIDSerializer.UUID_BYTE_LENGTH;
    int count =
        java.nio.ByteBuffer.wrap(data, countOffset, TaktLongListSerializer.COUNT_BYTE_LENGTH)
            .getInt();
    if (count < 0) {
      throw new SerializationException(
          "FlowNodeInstanceKeyDTO deserialization failed: negative path element count " + count);
    }
    return minimumLength + count * TaktLongListSerializer.LONG_BYTE_LENGTH;
  }
}
