/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import io.taktx.util.ProcessDefinitionKeySerializer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

public class SignalDefinitionSubscriptionKeySerializer
    implements Serializer<SignalDefinitionSubscriptionKeyDTO> {

  static final int STRING_LENGTH_BYTES = Short.BYTES;

  /**
   * @implSpec Encodes persisted store keys as {@code [32B signal-name SHA-256 hash | 2B
   *     process-definition-id byte length big-endian | UTF-8 process-definition-id bytes | 4B
   *     signed version big-endian | 2B element-id byte length big-endian | UTF-8 element-id
   *     bytes]}. For exclusive upper-bound sentinels produced from a shorter hash prefix, only that
   *     prefix is emitted so range scans can stop before the next hash bucket.
   */
  @Override
  public byte[] serialize(String topic, SignalDefinitionSubscriptionKeyDTO data) {
    return data == null ? null : toBytes(data);
  }

  public static byte[] toBytes(SignalDefinitionSubscriptionKeyDTO data) {
    if (data == null) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO serialization failed: key must not be null");
    }
    if (data.getSignalNameHash() == null || data.getSignalNameHash().length == 0) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO serialization failed: signalNameHash must not be null or empty");
    }

    if (data.getProcessDefinitionKey() == null || data.getElementId() == null) {
      byte[] upperBound = new byte[data.getSignalNameHash().length];
      System.arraycopy(data.getSignalNameHash(), 0, upperBound, 0, upperBound.length);
      return upperBound;
    }
    if (data.getSignalNameHash().length
        != SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO serialization failed: expected "
              + SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES
              + " hash bytes but got "
              + data.getSignalNameHash().length);
    }
    if (data.getProcessDefinitionKey() == null) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO serialization failed: processDefinitionKey must not be null");
    }
    if (data.getElementId() == null) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO serialization failed: elementId must not be null");
    }

    byte[] processDefinitionKeyBytes =
        ProcessDefinitionKeySerializer.toBytes(data.getProcessDefinitionKey());
    byte[] elementIdBytes = data.getElementId().getBytes(StandardCharsets.UTF_8);
    if (elementIdBytes.length > 0xFFFF) {
      throw new SerializationException(
          "SignalDefinitionSubscriptionKeyDTO serialization failed: elementId exceeds 65535 UTF-8 bytes");
    }

    ByteBuffer buffer =
        ByteBuffer.allocate(
            SignalInstanceSubscriptionKeySerializer.HASH_LENGTH_BYTES
                + processDefinitionKeyBytes.length
                + STRING_LENGTH_BYTES
                + elementIdBytes.length);
    buffer.put(data.getSignalNameHash());
    buffer.put(processDefinitionKeyBytes);
    buffer.putShort((short) elementIdBytes.length);
    buffer.put(elementIdBytes);
    return buffer.array();
  }
}
