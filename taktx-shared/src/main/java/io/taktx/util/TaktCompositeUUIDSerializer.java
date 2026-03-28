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

public class TaktCompositeUUIDSerializer
    implements org.apache.kafka.common.serialization.Serializer<java.util.UUID[]> {

  @Override
  public byte[] serialize(String topic, UUID[] data) {
    ByteBuffer bb = ByteBuffer.allocate(16 * data.length);
    for (UUID uuid : data) {
      bb.putLong(uuid.getMostSignificantBits());
      bb.putLong(uuid.getLeastSignificantBits());
    }
    return bb.array();
  }
}
