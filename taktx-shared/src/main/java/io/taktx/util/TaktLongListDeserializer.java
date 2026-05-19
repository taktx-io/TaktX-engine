/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class TaktLongListDeserializer implements Deserializer<List<Long>> {

  @Override
  public List<Long> deserialize(String topic, byte[] bytes) {
    return bytes == null ? null : fromBytes(bytes);
  }

  public static List<Long> fromBytes(byte[] bytes) {
    if (bytes == null) {
      throw new SerializationException("Long-list deserialization failed: key bytes are null");
    }
    if (bytes.length < TaktLongListSerializer.COUNT_BYTE_LENGTH) {
      throw new SerializationException(
          "Long-list deserialization failed: expected at least "
              + TaktLongListSerializer.COUNT_BYTE_LENGTH
              + " bytes but got "
              + bytes.length);
    }

    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    int size = buffer.getInt();
    if (size < 0) {
      throw new SerializationException(
          "Long-list deserialization failed: negative element count " + size);
    }

    int expectedLength =
        TaktLongListSerializer.COUNT_BYTE_LENGTH + size * TaktLongListSerializer.LONG_BYTE_LENGTH;
    if (bytes.length != expectedLength) {
      throw new SerializationException(
          "Long-list deserialization failed: expected "
              + expectedLength
              + " bytes for "
              + size
              + " elements but got "
              + bytes.length);
    }

    List<Long> result = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      result.add(buffer.getLong());
    }
    return result;
  }
}
