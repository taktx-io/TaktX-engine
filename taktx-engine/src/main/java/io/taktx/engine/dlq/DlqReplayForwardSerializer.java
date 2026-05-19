/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

/** Serializes replay-forward records as raw payload bytes while propagating replay headers. */
public class DlqReplayForwardSerializer implements Serializer<DlqReplayForwardRecord> {

  @Override
  public byte[] serialize(String topic, DlqReplayForwardRecord data) {
    return data == null ? null : data.payload();
  }

  @Override
  public byte[] serialize(String topic, Headers headers, DlqReplayForwardRecord data) {
    if (headers != null && data != null) {
      Map<String, byte[]> headerMap = data.headers();
      if (headerMap != null) {
        headerMap.forEach(
            (key, value) -> {
              if (value != null) {
                headers.remove(key);
                headers.add(key, value);
              }
            });
      }
    }
    return serialize(topic, data);
  }
}
