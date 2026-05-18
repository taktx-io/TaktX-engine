/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.MessageLite;
import org.apache.kafka.common.serialization.Serializer;

/** Kafka serializer for protobuf-lite messages. */
public class ProtoSerializer<T extends MessageLite> implements Serializer<T> {

  @Override
  public byte[] serialize(String topic, T data) {
    return data == null ? null : data.toByteArray();
  }
}

