/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
// NOTE: CBORFactory removed in PROTO-1.2; this entire class will be deleted in PROTO-1.3.
import java.io.IOException;
import lombok.Getter;
import org.apache.kafka.common.serialization.Serializer;

@Getter
public abstract class JsonSerializer<T> implements Serializer<T> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Class<T> clazz;

  protected JsonSerializer(Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public byte[] serialize(String topic, T data) {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(data);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
