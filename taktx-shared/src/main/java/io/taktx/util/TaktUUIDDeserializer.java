/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class TaktUUIDDeserializer implements Deserializer<UUID> {

  @Override
  public UUID deserialize(String topic, byte[] bytes) {
    return fromBytes(bytes);
  }

  public static UUID fromBytes(byte[] bytes) {
    if (bytes == null) {
      throw new SerializationException(
          "UUID deserialization failed: key bytes are null — expected "
              + TaktUUIDSerializer.UUID_BYTE_LENGTH
              + " bytes");
    }
    if (bytes.length != TaktUUIDSerializer.UUID_BYTE_LENGTH) {
      throw new SerializationException(
          "UUID deserialization failed: expected "
              + TaktUUIDSerializer.UUID_BYTE_LENGTH
              + " bytes but got "
              + bytes.length);
    }
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
