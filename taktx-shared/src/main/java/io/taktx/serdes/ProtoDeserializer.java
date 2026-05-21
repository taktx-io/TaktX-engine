/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/** Base Kafka deserializer for protobuf-lite messages. */
public abstract class ProtoDeserializer<T extends MessageLite> implements Deserializer<T> {

  protected abstract Parser<T> parser();

  @Override
  public T deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return parser().parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      throw new SerializationException("Failed to deserialize protobuf message", e);
    }
  }
}
