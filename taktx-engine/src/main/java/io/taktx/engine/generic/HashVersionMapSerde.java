/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import com.google.protobuf.InvalidProtocolBufferException;
import io.taktx.proto.HashVersionMapMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Proto-backed serde for the internal definition-hash → version lookup stores. */
public class HashVersionMapSerde implements Serde<Map<String, Integer>> {

  private final Serializer<Map<String, Integer>> serializer =
      (_, data) ->
          data == null
              ? null
              : HashVersionMapMessage.newBuilder().putAllVersionsByHash(data).build().toByteArray();

  private final Deserializer<Map<String, Integer>> deserializer =
      (_, data) -> {
        if (data == null) {
          return null;
        }
        try {
          return new LinkedHashMap<>(HashVersionMapMessage.parseFrom(data).getVersionsByHashMap());
        } catch (InvalidProtocolBufferException e) {
          throw new SerializationException("Failed to deserialize HashVersionMapMessage", e);
        }
      };

  @Override
  public Serializer<Map<String, Integer>> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<Map<String, Integer>> deserializer() {
    return deserializer;
  }
}
