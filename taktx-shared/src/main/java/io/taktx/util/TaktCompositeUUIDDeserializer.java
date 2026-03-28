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

public class TaktCompositeUUIDDeserializer
    implements org.apache.kafka.common.serialization.Deserializer<java.util.UUID[]> {

  @Override
  public UUID[] deserialize(String topic, byte[] data) {
    UUID[] uuids = new UUID[data.length / 16];
    int index = 0;
    ByteBuffer bb = ByteBuffer.wrap(data);
    while (bb.hasRemaining()) {
      long msb = bb.getLong();
      long lsb = bb.getLong();
      uuids[index++] = new UUID(msb, lsb);
    }
    return uuids;
  }
}
