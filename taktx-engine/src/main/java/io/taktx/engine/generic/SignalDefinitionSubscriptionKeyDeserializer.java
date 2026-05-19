/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.util.ProcessDefinitionKeyDeserializer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class SignalDefinitionSubscriptionKeyDeserializer
    implements Deserializer<SignalDefinitionSubscriptionKeyDTO> {

  @Override
  public SignalDefinitionSubscriptionKeyDTO deserialize(String topic, byte[] data) {
    return data == null ? null : fromBytes(data);
  }

  public static SignalDefinitionSubscriptionKeyDTO fromBytes(byte[] data) {
    if (data == null) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO deserialization failed: key bytes are null");
    }

    int hashLength = SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES;
    int minimumLength =
        hashLength
            + ProcessDefinitionKeySerializerLength.minimumBytes()
            + SignalDefinitionSubscriptionKeySerializer.STRING_LENGTH_BYTES;
    if (data.length < minimumLength) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO deserialization failed: expected at least "
              + minimumLength
              + " bytes but got "
              + data.length);
    }

    byte[] hash = Arrays.copyOfRange(data, 0, hashLength);
    int processDefinitionOffset = hashLength;
    int processDefinitionEndOffset =
        ProcessDefinitionKeyDeserializer.serializedLength(data, processDefinitionOffset);
    int processDefinitionLength = processDefinitionEndOffset - processDefinitionOffset;
    byte[] processDefinitionBytes =
        Arrays.copyOfRange(
            data, processDefinitionOffset, processDefinitionOffset + processDefinitionLength);

    int elementIdLengthOffset = processDefinitionOffset + processDefinitionLength;
    if (data.length
        < elementIdLengthOffset + SignalDefinitionSubscriptionKeySerializer.STRING_LENGTH_BYTES) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO deserialization failed: missing elementId length");
    }

    ByteBuffer buffer =
        ByteBuffer.wrap(data, elementIdLengthOffset, data.length - elementIdLengthOffset);
    int elementIdLength = Short.toUnsignedInt(buffer.getShort());
    int expectedLength =
        elementIdLengthOffset
            + SignalDefinitionSubscriptionKeySerializer.STRING_LENGTH_BYTES
            + elementIdLength;
    if (data.length != expectedLength) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO deserialization failed: expected "
              + expectedLength
              + " bytes but got "
              + data.length);
    }

    byte[] elementIdBytes = Arrays.copyOfRange(data, elementIdLengthOffset + 2, expectedLength);
    ProcessDefinitionKey processDefinitionKey =
        ProcessDefinitionKeyDeserializer.fromBytes(processDefinitionBytes);
    return new SignalDefinitionSubscriptionKeyDTO(
        hash, processDefinitionKey, new String(elementIdBytes, StandardCharsets.UTF_8));
  }

  private static final class ProcessDefinitionKeySerializerLength {
    private ProcessDefinitionKeySerializerLength() {}

    private static int minimumBytes() {
      return Short.BYTES + Integer.BYTES;
    }
  }
}
