/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.MessageLite;
import lombok.Getter;
import org.apache.kafka.common.serialization.Serializer;

/** Base serializer for DTOs that are first mapped to protobuf messages. */
@Getter
public abstract class ProtoMappedSerializer<T> implements Serializer<T> {

  private final Class<T> clazz;

  protected ProtoMappedSerializer(Class<T> clazz) {
    this.clazz = clazz;
  }

  protected abstract MessageLite toProto(T data);

  @Override
  public byte[] serialize(String topic, T data) {
    return data == null ? null : toProto(data).toByteArray();
  }
}
