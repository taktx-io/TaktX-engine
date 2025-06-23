/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.util;

import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class TaktCompositeUUIDSerde implements Serde<UUID[]> {

  private final Serializer<UUID[]> serializer;
  private final Deserializer<UUID[]> deserializer;

  public TaktCompositeUUIDSerde() {
    serializer = new TaktCompositeUUIDSerializer();
    deserializer = new TaktCompositeUUIDDeserializer();
  }

  @Override
  public Serializer<UUID[]> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<UUID[]> deserializer() {
    return deserializer;
  }
}
