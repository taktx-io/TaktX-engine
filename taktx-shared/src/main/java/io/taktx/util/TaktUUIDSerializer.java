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
import org.apache.kafka.common.serialization.Serializer;

public class TaktUUIDSerializer implements Serializer<UUID> {

  public static final int UUID_BYTE_LENGTH = 16;

  /**
   * @implSpec Encodes the UUID as {@code [8B most-significant-bits big-endian | 8B
   *     least-significant-bits big-endian]}.
   */
  @Override
  public byte[] serialize(String topic, UUID uuid) {
    return uuid == null ? null : toBytes(uuid);
  }

  public static byte[] toBytes(UUID uuid) {
    if (uuid == null) {
      throw new SerializationException("UUID serialization failed: key must not be null");
    }
    ByteBuffer buffer = ByteBuffer.allocate(UUID_BYTE_LENGTH);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }
}
