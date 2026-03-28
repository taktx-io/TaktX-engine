/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ZippedStringSerde implements Serde<String> {

  @Override
  public Serializer<String> serializer() {
    return new ZippedStringSerializer();
  }

  @Override
  public Deserializer<String> deserializer() {
    return new ZippedStringDeserializer();
  }
}
