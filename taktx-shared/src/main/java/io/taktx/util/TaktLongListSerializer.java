/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import java.nio.ByteBuffer;
import java.util.List;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

public class TaktLongListSerializer implements Serializer<List<Long>> {

  public static final int COUNT_BYTE_LENGTH = Integer.BYTES;
  public static final int LONG_BYTE_LENGTH = Long.BYTES;

  /**
   * @implSpec Encodes the list as {@code [4B element-count big-endian | 8B×n signed long values
   *     big-endian]}.
   */
  @Override
  public byte[] serialize(String topic, List<Long> longList) {
    return longList == null ? null : toBytes(longList);
  }

  public static byte[] toBytes(List<Long> longList) {
    if (longList == null) {
      throw new SerializationException("Long-list serialization failed: key must not be null");
    }
    ByteBuffer buffer = ByteBuffer.allocate(COUNT_BYTE_LENGTH + LONG_BYTE_LENGTH * longList.size());
    buffer.putInt(longList.size());
    for (Long value : longList) {
      if (value == null) {
        throw new SerializationException(
            "Long-list serialization failed: key must not contain null elements");
      }
      buffer.putLong(value);
    }
    return buffer.array();
  }
}
