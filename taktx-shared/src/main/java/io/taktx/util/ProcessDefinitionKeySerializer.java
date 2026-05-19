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
import org.apache.kafka.common.serialization.Serializer;

public class ProcessDefinitionKeySerializer implements Serializer<ProcessDefinitionKey> {

  static final int PROCESS_DEFINITION_ID_LENGTH_BYTES = Short.BYTES;
  static final int VERSION_BYTE_LENGTH = Integer.BYTES;

  /**
   * @implSpec Encodes the key as {@code [2B process-definition-id byte length big-endian | UTF-8
   *     process-definition-id bytes | 4B signed version big-endian]}.
   */
  @Override
  public byte[] serialize(String topic, ProcessDefinitionKey data) {
    return data == null ? null : toBytes(data);
  }

  public static byte[] toBytes(ProcessDefinitionKey data) {
    if (data == null) {
      throw new SerializationException(
          "ProcessDefinitionKey serialization failed: key must not be null");
    }
    if (data.getProcessDefinitionId() == null) {
      throw new SerializationException(
          "ProcessDefinitionKey serialization failed: processDefinitionId must not be null");
    }
    if (data.getVersion() == null) {
      throw new SerializationException(
          "ProcessDefinitionKey serialization failed: version must not be null");
    }

    byte[] processDefinitionIdBytes =
        data.getProcessDefinitionId().getBytes(StandardCharsets.UTF_8);
    if (processDefinitionIdBytes.length > 0xFFFF) {
      throw new SerializationException(
          "ProcessDefinitionKey serialization failed: processDefinitionId exceeds 65535 UTF-8 bytes");
    }

    ByteBuffer buffer =
        ByteBuffer.allocate(
            PROCESS_DEFINITION_ID_LENGTH_BYTES
                + processDefinitionIdBytes.length
                + VERSION_BYTE_LENGTH);
    buffer.putShort((short) processDefinitionIdBytes.length);
    buffer.put(processDefinitionIdBytes);
    buffer.putInt(data.getVersion());
    return buffer.array();
  }
}
