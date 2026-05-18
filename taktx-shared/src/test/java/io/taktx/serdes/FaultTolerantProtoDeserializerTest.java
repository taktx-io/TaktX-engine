/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Parser;
import io.taktx.dto.Constants;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.proto.UserTaskTriggerMessage;
import io.taktx.security.Ed25519Service;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningKeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FaultTolerantProtoDeserializerTest {

  private static final String TOPIC = "proto-security-test";

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
  }

  @Test
  void validProtoBytesWithValidSignature_returnSuccess() {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());

    UserTaskTriggerMessage message =
        UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-1").build();
    byte[] bytes = message.toByteArray();

    RecordHeaders headers = signedHeaders(bytes, privateKeyBase64);

    DeserializationResult<UserTaskTriggerMessage> result;
    try (SignValidatingDeserializer deserializer = new SignValidatingDeserializer()) {
      deserializer.configure(
          Map.of(FaultTolerantProtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);
      result = deserializer.deserialize(TOPIC, headers, bytes);
    }

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasValue()).isTrue();
    assertThat(result.getValue()).isEqualTo(message);
    assertThat(result.getError()).isNull();
  }

  @Test
  void corruptBytes_returnFailureWithoutValue() {
    byte[] corrupt = {0x0A, 0x02, 0x01};

    DeserializationResult<UserTaskTriggerMessage> result;
    try (SignValidatingDeserializer deserializer = new SignValidatingDeserializer()) {
      result = deserializer.deserialize(TOPIC, corrupt);
    }

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasValue()).isFalse();
    assertThat(result.getValue()).isNull();
    assertThat(result.getError()).contains("Failed to decode body as UserTaskTriggerMessage");
  }

  @Test
  void invalidSignature_returnsDecodedBodyWithError() {
    KeyPair trustedKp = SigningKeyGenerator.generate();
    String trustedPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(trustedKp.getPublic());

    KeyPair otherKp = SigningKeyGenerator.generate();
    String otherPrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(otherKp.getPrivate());

    UserTaskTriggerMessage message =
        UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-2").build();
    byte[] bytes = message.toByteArray();

    RecordHeaders headers = signedHeaders(bytes, otherPrivateKeyBase64);

    DeserializationResult<UserTaskTriggerMessage> result;
    try (SignValidatingDeserializer deserializer = new SignValidatingDeserializer()) {
      deserializer.configure(
          Map.of(FaultTolerantProtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, trustedPublicKeyBase64),
          false);
      result = deserializer.deserialize(TOPIC, headers, bytes);
    }

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasValue()).isTrue();
    assertThat(result.getValue()).isEqualTo(message);
    assertThat(result.getError()).contains("signature verification failed");
  }

  @Test
  void signingRequiredWithoutHeader_returnsDecodedBodyWithError() {
    UserTaskTriggerMessage message =
        UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-3").build();
    byte[] bytes = message.toByteArray();

    DeserializationResult<UserTaskTriggerMessage> result;
    try (SignValidatingDeserializer deserializer = new SignValidatingDeserializer()) {
      deserializer.configure(
          Map.of(
              FaultTolerantProtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
              "static-key-placeholder",
              FaultTolerantProtoDeserializer.SIGNING_REQUIRED_CONFIG,
              "true"),
          false);
      result = deserializer.deserialize(TOPIC, new RecordHeaders(), bytes);
    }

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasValue()).isTrue();
    assertThat(result.getValue()).isEqualTo(message);
    assertThat(result.getError()).contains(Constants.HEADER_ENGINE_SIGNATURE).contains("=true");
  }

  @Test
  void runtimeSigningFlagCanRequireHeaderWithoutReconfigure() {
    UserTaskTriggerMessage message =
        UserTaskTriggerMessage.newBuilder().setUserTaskId("user-task-4").build();
    byte[] bytes = message.toByteArray();

    DeserializationResult<UserTaskTriggerMessage> unsignedRejected;
    try (SignValidatingDeserializer deserializer = new SignValidatingDeserializer()) {
      deserializer.configure(
          Map.of(FaultTolerantProtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, "static-key-placeholder"),
          false);

      RuntimeConfigurationHolder.set(
          GlobalConfigurationDTO.builder().signingEnabled(false).build());
      DeserializationResult<UserTaskTriggerMessage> unsignedOk =
          deserializer.deserialize(TOPIC, new RecordHeaders(), bytes);
      assertThat(unsignedOk.isSuccess()).isTrue();
      assertThat(unsignedOk.getValue()).isEqualTo(message);

      RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
      unsignedRejected = deserializer.deserialize(TOPIC, new RecordHeaders(), bytes);
    }

    assertThat(unsignedRejected.isSuccess()).isFalse();
    assertThat(unsignedRejected.hasValue()).isTrue();
    assertThat(unsignedRejected.getValue()).isEqualTo(message);
    assertThat(unsignedRejected.getError()).contains(Constants.HEADER_ENGINE_SIGNATURE);
  }

  private static RecordHeaders signedHeaders(byte[] payload, String privateKeyBase64) {
    byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
    String sigHeader = "engine-key." + Base64.getEncoder().encodeToString(sig);
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, sigHeader.getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private static final class SignValidatingDeserializer
      extends FaultTolerantProtoDeserializer<UserTaskTriggerMessage> {

    private SignValidatingDeserializer() {
      super(UserTaskTriggerMessage.class, true);
    }

    @Override
    protected Parser<UserTaskTriggerMessage> parser() {
      return UserTaskTriggerMessage.parser();
    }
  }
}
