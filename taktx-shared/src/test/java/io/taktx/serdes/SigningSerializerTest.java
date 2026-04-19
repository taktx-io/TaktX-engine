/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.Constants;
import io.taktx.security.SigningServiceHolder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SigningSerializerTest {

  @AfterEach
  void tearDown() {
    SigningServiceHolder.clear();
  }

  @Test
  void nullPayload_signsEmptyByteArrayAndKeepsTombstoneValueNull() {
    AtomicReference<byte[]> signedPayload = new AtomicReference<>();
    SigningServiceHolder.set(
        payload -> {
          signedPayload.set(payload);
          return "engine-key.AABB";
        });

    SigningSerializer<String> serializer =
        new SigningSerializer<>(
            new Serializer<>() {
              @Override
              public byte[] serialize(String topic, String data) {
                return null;
              }

              @Override
              public byte[] serialize(String topic, Headers headers, String data) {
                return null;
              }
            });
    RecordHeaders headers = new RecordHeaders();

    byte[] result = serializer.serialize("schedule-commands", headers, null);

    assertThat(result).isNull();
    assertThat(signedPayload.get()).isNotNull().isEmpty();
    assertThat(headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE)).isNotNull();
    assertThat(
            new String(
                headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE).value(),
                StandardCharsets.UTF_8))
        .isEqualTo("engine-key.AABB");
  }
}
