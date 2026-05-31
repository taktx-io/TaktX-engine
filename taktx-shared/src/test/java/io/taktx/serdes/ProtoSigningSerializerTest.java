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
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.ExternalTaskTriggerMessage;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProtoSigningSerializerTest {

  private static final String TOPIC = "proto-signing-test";

  @AfterEach
  void tearDown() {
    SigningServiceHolder.clear();
  }

  @Test
  void serialize_returnsExactProtoBytesThatRoundTripByParsing() throws Exception {
    ExternalTaskTriggerDTO dto = sampleExternalTaskTrigger();
    ExternalTaskTriggerMessage expected = WorkerTriggerProtoMapper.toProto(dto);

    byte[] bytes;
    try (ProtoSigningSerializer<ExternalTaskTriggerDTO> serializer =
        new ProtoSigningSerializer<>(WorkerTriggerProtoMapper::toProto)) {
      bytes = serializer.serialize(TOPIC, dto);
    }

    assertThat(bytes).isEqualTo(expected.toByteArray());
    assertThat(ExternalTaskTriggerMessage.parseFrom(bytes)).isEqualTo(expected);
  }

  @Test
  void serializeWithHeaders_addsVerifiableTxSigHeader() {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    SigningServiceHolder.set(
        payload -> {
          byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
          return "engine-key." + Base64.getEncoder().encodeToString(sig);
        });

    RecordHeaders headers = new RecordHeaders();
    byte[] bytes;
    try (ProtoSigningSerializer<ExternalTaskTriggerDTO> serializer =
        new ProtoSigningSerializer<>(WorkerTriggerProtoMapper::toProto)) {
      bytes = serializer.serialize(TOPIC, headers, sampleExternalTaskTrigger());
    }

    assertThat(bytes).isNotNull().isNotEmpty();
    assertThat(headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE)).isNotNull();

    String headerValue =
        new String(
            headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE).value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    assertThat(dot).isGreaterThan(0);
    assertThat(headerValue.substring(0, dot)).isEqualTo("engine-key");

    byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(dot + 1));
    assertThat(Ed25519Service.verify(bytes, sigBytes, publicKeyBase64)).isTrue();
  }

  @Test
  void serializeWithHeaders_prefersLocalSigningFunctionOverGlobalHolder() {
    SigningServiceHolder.set(payload -> "global-key.AABB");

    RecordHeaders headers = new RecordHeaders();
    try (ProtoSigningSerializer<ExternalTaskTriggerDTO> serializer =
        new ProtoSigningSerializer<>(
            WorkerTriggerProtoMapper::toProto, () -> payload -> "local-key.CCDD")) {
      serializer.serialize(TOPIC, headers, sampleExternalTaskTrigger());
    }

    String headerValue =
        new String(
            headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE).value(), StandardCharsets.UTF_8);
    assertThat(headerValue).isEqualTo("local-key.CCDD");
  }

  @Test
  void nullPayload_signsEmptyByteArrayAndKeepsTombstoneValueNull() {
    byte[][] signedPayload = new byte[1][];
    SigningServiceHolder.set(
        payload -> {
          signedPayload[0] = payload;
          return "engine-key.AABB";
        });

    RecordHeaders headers = new RecordHeaders();
    byte[] result;
    try (ProtoSigningSerializer<ExternalTaskTriggerDTO> serializer =
        new ProtoSigningSerializer<>(WorkerTriggerProtoMapper::toProto)) {
      result = serializer.serialize(TOPIC, headers, null);
    }

    assertThat(result).isNull();
    assertThat(signedPayload[0]).isNotNull().isEmpty();
    assertThat(headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE)).isNotNull();
  }

  private static ExternalTaskTriggerDTO sampleExternalTaskTrigger() {
    return new ExternalTaskTriggerDTO(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        new ProcessDefinitionKey("service-task", 3),
        "payment-worker",
        "serviceTask",
        List.of(4L, 8L),
        VariablesDTO.of("status", "ok"),
        Map.of("worker", "billing", "priority", "high"));
  }
}
