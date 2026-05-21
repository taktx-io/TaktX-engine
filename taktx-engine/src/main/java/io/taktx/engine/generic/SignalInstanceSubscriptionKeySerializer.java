/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import io.taktx.util.TaktLongListSerializer;
import io.taktx.util.TaktUUIDSerializer;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

public class SignalInstanceSubscriptionKeySerializer
    implements Serializer<SignalInstanceSubscriptionKeyDTO> {

  public static final int HASH_LENGTH_BYTES = 32;

  /**
   * @implSpec Encodes persisted store keys as {@code [32B signal-name SHA-256 hash | 16B
   *     process-instance UUID big-endian | 4B path-element count big-endian | 8B×n path elements
   *     big-endian]}.
   */
  @Override
  public byte[] serialize(String topic, SignalInstanceSubscriptionKeyDTO data) {
    return data == null ? null : toBytes(data);
  }

  public static byte[] toBytes(SignalInstanceSubscriptionKeyDTO data) {
    if (data == null) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO serialization failed: key must not be null");
    }
    if (data.getSignalNameHash() == null) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO serialization failed: signalNameHash must not be null");
    }
    if (data.getSignalNameHash().length != HASH_LENGTH_BYTES) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO serialization failed: expected "
              + HASH_LENGTH_BYTES
              + " hash bytes but got "
              + data.getSignalNameHash().length);
    }
    if (data.getProcessInstanceId() == null) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO serialization failed: processInstanceId must not be null");
    }
    if (data.getElementInstanceIdPath() == null) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO serialization failed: elementInstanceIdPath must not be null");
    }

    byte[] uuidBytes = TaktUUIDSerializer.toBytes(data.getProcessInstanceId());
    byte[] pathBytes = TaktLongListSerializer.toBytes(data.getElementInstanceIdPath());
    byte[] serialized = new byte[HASH_LENGTH_BYTES + uuidBytes.length + pathBytes.length];
    System.arraycopy(data.getSignalNameHash(), 0, serialized, 0, HASH_LENGTH_BYTES);
    System.arraycopy(uuidBytes, 0, serialized, HASH_LENGTH_BYTES, uuidBytes.length);
    System.arraycopy(
        pathBytes, 0, serialized, HASH_LENGTH_BYTES + uuidBytes.length, pathBytes.length);
    return serialized;
  }
}
