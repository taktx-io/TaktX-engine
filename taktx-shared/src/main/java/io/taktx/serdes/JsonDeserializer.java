/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import lombok.Getter;
import org.apache.kafka.common.serialization.Deserializer;

@Getter
public abstract class JsonDeserializer<T> implements Deserializer<T> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  private final Class<T> clazz;

  protected JsonDeserializer(Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public T deserialize(String s, byte[] bytes) {
    try {
      return OBJECT_MAPPER.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
