/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import io.taktx.util.TaktLongListDeserializer;
import io.taktx.util.TaktLongListSerializer;
import io.taktx.util.TaktUUIDDeserializer;
import io.taktx.util.TaktUUIDSerializer;
import java.util.Arrays;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class SignalInstanceSubscriptionKeyDeserializer
    implements Deserializer<SignalInstanceSubscriptionKeyDTO> {

  @Override
  public SignalInstanceSubscriptionKeyDTO deserialize(String topic, byte[] data) {
    return data == null ? null : fromBytes(data);
  }

  public static SignalInstanceSubscriptionKeyDTO fromBytes(byte[] data) {
    if (data == null) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO deserialization failed: key bytes are null");
    }

    int minimumLength =
        SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES
            + TaktUUIDSerializer.UUID_BYTE_LENGTH
            + TaktLongListSerializer.COUNT_BYTE_LENGTH;
    if (data.length < minimumLength) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO deserialization failed: expected at least "
              + minimumLength
              + " bytes but got "
              + data.length);
    }

    byte[] hash =
        Arrays.copyOfRange(data, 0, SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES);
    byte[] uuidBytes =
        Arrays.copyOfRange(
            data,
            SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES,
            SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES
                + TaktUUIDSerializer.UUID_BYTE_LENGTH);
    byte[] pathBytes =
        Arrays.copyOfRange(
            data,
            SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES
                + TaktUUIDSerializer.UUID_BYTE_LENGTH,
            data.length);

    int expectedLength = minimumLength + count(pathBytes) * TaktLongListSerializer.LONG_BYTE_LENGTH;
    if (data.length != expectedLength) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO deserialization failed: expected "
              + expectedLength
              + " bytes but got "
              + data.length);
    }

    return new SignalInstanceSubscriptionKeyDTO(
        hash,
        TaktUUIDDeserializer.fromBytes(uuidBytes),
        TaktLongListDeserializer.fromBytes(pathBytes));
  }

  private static int count(byte[] pathBytes) {
    int count =
        java.nio.ByteBuffer.wrap(pathBytes, 0, TaktLongListSerializer.COUNT_BYTE_LENGTH).getInt();
    if (count < 0) {
      throw new SerializationException(
          "SignalInstanceSubscriptionKeyDTO deserialization failed: negative path element count "
              + count);
    }
    return count;
  }
}
