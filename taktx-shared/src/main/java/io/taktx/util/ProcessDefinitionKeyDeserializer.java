/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import io.taktx.dto.ProcessDefinitionKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class ProcessDefinitionKeyDeserializer implements Deserializer<ProcessDefinitionKey> {

  @Override
  public ProcessDefinitionKey deserialize(String topic, byte[] data) {
    return data == null ? null : fromBytes(data);
  }

  public static ProcessDefinitionKey fromBytes(byte[] data) {
    int expectedLength = serializedLength(data, 0);
    if (data.length != expectedLength) {
      throw new SerializationException(
          "ProcessDefinitionKey deserialization failed: expected "
              + expectedLength
              + " bytes but got "
              + data.length);
    }

    ByteBuffer buffer = ByteBuffer.wrap(data);
    int processDefinitionIdLength = Short.toUnsignedInt(buffer.getShort());
    byte[] processDefinitionIdBytes = new byte[processDefinitionIdLength];
    buffer.get(processDefinitionIdBytes);
    return new ProcessDefinitionKey(
        new String(processDefinitionIdBytes, StandardCharsets.UTF_8), buffer.getInt());
  }

  public static int serializedLength(byte[] data, int offset) {
    if (data == null) {
      throw new SerializationException(
          "ProcessDefinitionKey deserialization failed: key bytes are null");
    }
    int minimumLength =
        offset
            + ProcessDefinitionKeySerializer.PROCESS_DEFINITION_ID_LENGTH_BYTES
            + ProcessDefinitionKeySerializer.VERSION_BYTE_LENGTH;
    if (data.length < minimumLength) {
      throw new SerializationException(
          "ProcessDefinitionKey deserialization failed: expected at least "
              + minimumLength
              + " bytes but got "
              + data.length);
    }

    int processDefinitionIdLength =
        Short.toUnsignedInt(
            ByteBuffer.wrap(
                    data, offset, ProcessDefinitionKeySerializer.PROCESS_DEFINITION_ID_LENGTH_BYTES)
                .getShort());
    return minimumLength + processDefinitionIdLength;
  }
}
