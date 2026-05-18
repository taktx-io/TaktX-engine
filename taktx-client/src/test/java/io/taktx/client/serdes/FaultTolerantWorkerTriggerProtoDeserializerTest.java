/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import io.taktx.serdes.DeserializationResult;
import io.taktx.serdes.ExternalTaskTriggerProtoSerializer;
import io.taktx.serdes.FaultTolerantProtoDeserializer;
import io.taktx.serdes.ProtoSigningSerializer;
import io.taktx.serdes.UserTaskTriggerProtoSerializer;
import io.taktx.serdes.WorkerTriggerProtoMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FaultTolerantWorkerTriggerProtoDeserializerTest {

  private static final String TOPIC = "worker-trigger-topic";

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
    SigningServiceHolder.clear();
  }

  @Test
  void externalTaskTrigger_validSignedProto_returnsDtoSuccess() {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());

    ExternalTaskTriggerDTO dto =
        new ExternalTaskTriggerDTO(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            new ProcessDefinitionKey("shipping", 1),
            "ship-job",
            "shipTask",
            List.of(9L),
            VariablesDTO.of("approved", true),
            Map.of("channel", "warehouse"));

    DeserializationResult<ExternalTaskTriggerDTO> result;
    RecordHeaders headers = new RecordHeaders();
    SigningServiceHolder.set(
        payload -> {
          byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
          return "engine-key." + Base64.getEncoder().encodeToString(sig);
        });
    try (ProtoSigningSerializer<ExternalTaskTriggerDTO> serializer =
            new ProtoSigningSerializer<>(WorkerTriggerProtoMapper::toProto);
        FaultTolerantExternalTaskTriggerDeserializer deserializer =
            new FaultTolerantExternalTaskTriggerDeserializer()) {
      byte[] payload = serializer.serialize(TOPIC, headers, dto);
      deserializer.configure(
          Map.of(FaultTolerantProtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);
      result = deserializer.deserialize(TOPIC, headers, payload);
    }

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getValue()).isEqualTo(dto);
    assertThat(result.getError()).isNull();
  }

  @Test
  void externalTaskTrigger_invalidSignature_returnsDecodedDtoWithError() {
    KeyPair trustedKp = SigningKeyGenerator.generate();
    String trustedPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(trustedKp.getPublic());

    KeyPair otherKp = SigningKeyGenerator.generate();
    String otherPrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(otherKp.getPrivate());

    ExternalTaskTriggerDTO dto =
        new ExternalTaskTriggerDTO(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            new ProcessDefinitionKey("shipping", 2),
            "ship-job",
            "shipTask",
            List.of(5L, 6L),
            VariablesDTO.of("approved", false),
            Map.of());

    DeserializationResult<ExternalTaskTriggerDTO> result;
    try (ExternalTaskTriggerProtoSerializer serializer = new ExternalTaskTriggerProtoSerializer();
        FaultTolerantExternalTaskTriggerDeserializer deserializer =
            new FaultTolerantExternalTaskTriggerDeserializer()) {
      byte[] payload = serializer.serialize(TOPIC, dto);
      RecordHeaders headers = signedHeaders(payload, otherPrivateKeyBase64);
      deserializer.configure(
          Map.of(
              FaultTolerantProtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
              trustedPublicKeyBase64),
          false);
      result = deserializer.deserialize(TOPIC, headers, payload);
    }

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasValue()).isTrue();
    assertThat(result.getValue()).isEqualTo(dto);
    assertThat(result.getError()).contains("signature verification failed");
  }

  @Test
  void userTaskTrigger_signingRequiredWithoutHeader_returnsDecodedDtoWithError() {
    UserTaskTriggerDTO dto =
        new UserTaskTriggerDTO(
            UUID.fromString("55555555-5555-5555-5555-555555555555"),
            new ProcessDefinitionKey("approval", -1),
            "approve-order",
            List.of(1L, 2L),
            null,
            null,
            null,
            VariablesDTO.of("amount", 42L));

    DeserializationResult<UserTaskTriggerDTO> result;
    try (UserTaskTriggerProtoSerializer serializer = new UserTaskTriggerProtoSerializer();
        FaultTolerantUserTaskTriggerDeserializer deserializer =
            new FaultTolerantUserTaskTriggerDeserializer()) {
      byte[] payload = serializer.serialize(TOPIC, dto);
      deserializer.configure(
          Map.of(
              FaultTolerantProtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
              "static-key-placeholder",
              FaultTolerantProtoDeserializer.SIGNING_REQUIRED_CONFIG,
              "true"),
          false);
      result = deserializer.deserialize(TOPIC, new RecordHeaders(), payload);
    }

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasValue()).isTrue();
    assertThat(result.getValue()).isEqualTo(dto);
    assertThat(result.getError()).contains(Constants.HEADER_ENGINE_SIGNATURE).contains("=true");
  }

  private static RecordHeaders signedHeaders(byte[] payload, String privateKeyBase64) {
    byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
    String sigHeader = "engine-key." + Base64.getEncoder().encodeToString(sig);
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, sigHeader.getBytes(StandardCharsets.UTF_8));
    return headers;
  }
}

