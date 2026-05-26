/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.protobuf.Parser;
import io.taktx.dto.Constants;
import io.taktx.proto.UserTaskTriggerMessage;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProtoDtoDeserializerTest {

  private static final String TOPIC = "proto-dto-test";

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
    EngineSigningKeysHolder.clear();
    SigningKeysStoreHolder.clear();
  }

  @Test
  void deserialize_nullPayload_returnsNull() {
    try (StringUserTaskDeserializer deserializer = new StringUserTaskDeserializer()) {
      assertThat(deserializer.deserialize(TOPIC, null)).isNull();
    }
  }

  @Test
  void configure_withoutStaticKey_usesRegisteredSigningKeyStore() {
    SigningKeysStore store = mock(SigningKeysStore.class);
    SigningKeysStoreHolder.set(store);

    try (StringUserTaskDeserializer deserializer = new StringUserTaskDeserializer()) {
      deserializer.configure(Map.of(), false);

      assertThat(deserializer.getSigningKeysStore()).isSameAs(store);
      assertThat(deserializer.getEnginePublicKeyBase64()).isNull();
    }
  }

  @Test
  void deserialize_validSignature_returnsMappedDto() {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    UserTaskTriggerMessage message =
        UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-123").build();
    byte[] bytes = message.toByteArray();

    try (StringUserTaskDeserializer deserializer = new StringUserTaskDeserializer()) {
      deserializer.configure(
          Map.of(ProtoDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);

      String actual =
          deserializer.deserialize(TOPIC, signedHeaders(bytes, privateKeyBase64), bytes);

      assertThat(actual).isEqualTo("user-task-123");
    }
  }

  @Test
  void deserialize_missingSignatureWhenRequired_throwsIllegalStateException() {
    UserTaskTriggerMessage message =
        UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-123").build();
    byte[] bytes = message.toByteArray();
    RecordHeaders headers = new RecordHeaders();

    try (StringUserTaskDeserializer deserializer = new StringUserTaskDeserializer()) {
      deserializer.configure(
          Map.of(
              ProtoDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
              "static-key-placeholder",
              ProtoDtoDeserializer.SIGNING_REQUIRED_CONFIG,
              "true"),
          false);

      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, bytes))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(Constants.HEADER_ENGINE_SIGNATURE)
          .hasMessageContaining("=true");
    }
  }

  @Test
  void deserialize_malformedSignatureHeader_throwsIllegalStateException() {
    UserTaskTriggerMessage message =
        UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-123").build();
    byte[] bytes = message.toByteArray();
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, "bad-header".getBytes(StandardCharsets.UTF_8));

    try (StringUserTaskDeserializer deserializer = new StringUserTaskDeserializer()) {
      deserializer.configure(
          Map.of(ProtoDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, "static-key-placeholder"), false);

      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, bytes))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Malformed")
          .hasMessageContaining(Constants.HEADER_ENGINE_SIGNATURE);
    }
  }

  private static RecordHeaders signedHeaders(byte[] payload, String privateKeyBase64) {
    byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
    String sigHeader = "engine-key." + Base64.getEncoder().encodeToString(sig);
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, sigHeader.getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private static final class StringUserTaskDeserializer
      extends ProtoDtoDeserializer<String, UserTaskTriggerMessage> {

    private StringUserTaskDeserializer() {
      super(String.class, true);
    }

    @Override
    protected Parser<UserTaskTriggerMessage> parser() {
      return UserTaskTriggerMessage.parser();
    }

    @Override
    protected String toDto(UserTaskTriggerMessage message) {
      return message.getUserTaskId();
    }
  }
}
