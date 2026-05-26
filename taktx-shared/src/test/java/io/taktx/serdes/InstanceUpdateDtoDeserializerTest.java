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
import static org.mockito.Mockito.when;

import io.taktx.dto.Constants;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
import io.taktx.variables.Variables;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InstanceUpdateDtoDeserializerTest {

  private static final String TOPIC = "instance-update";

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
    EngineSigningKeysHolder.clear();
    SigningKeysStoreHolder.clear();
  }

  @Test
  void deserialize_nullPayload_returnsNull() {
    try (InstanceUpdateDtoDeserializer deserializer = new InstanceUpdateDtoDeserializer()) {
      assertThat(deserializer.deserialize(TOPIC, null)).isNull();
    }
  }

  @Test
  void deserialize_invalidProtoBytes_throwsSerializationException() {
    byte[] invalid = {0x0A, 0x02, 0x01};

    try (InstanceUpdateDtoDeserializer deserializer = new InstanceUpdateDtoDeserializer()) {
      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, invalid))
          .isInstanceOf(SerializationException.class)
          .hasMessageContaining("Failed to deserialize InstanceUpdateEnvelope");
    }
  }

  @Test
  void configure_withoutStaticKey_usesRegisteredSigningKeyStore() {
    SigningKeysStore store = mock(SigningKeysStore.class);
    SigningKeysStoreHolder.set(store);

    try (InstanceUpdateDtoDeserializer deserializer = new InstanceUpdateDtoDeserializer()) {
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
    InstanceUpdateDTO expected = sampleUpdate();
    byte[] bytes = InstanceUpdateProtoMapper.toProto(expected).toByteArray();

    try (InstanceUpdateDtoDeserializer deserializer = new InstanceUpdateDtoDeserializer()) {
      deserializer.configure(
          Map.of(InstanceUpdateDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);

      InstanceUpdateDTO actual =
          deserializer.deserialize(
              TOPIC, signedHeaders("engine-key", bytes, privateKeyBase64), bytes);

      assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
  }

  @Test
  void deserialize_missingSignatureWhenRequired_throwsIllegalStateException() {
    InstanceUpdateDTO update = sampleUpdate();
    byte[] bytes = InstanceUpdateProtoMapper.toProto(update).toByteArray();
    RecordHeaders headers = new RecordHeaders();

    try (InstanceUpdateDtoDeserializer deserializer = new InstanceUpdateDtoDeserializer()) {
      deserializer.configure(
          Map.of(
              InstanceUpdateDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
              "static-key-placeholder",
              InstanceUpdateDtoDeserializer.SIGNING_REQUIRED_CONFIG,
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
    InstanceUpdateDTO update = sampleUpdate();
    byte[] bytes = InstanceUpdateProtoMapper.toProto(update).toByteArray();
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, "missing-dot".getBytes(StandardCharsets.UTF_8));

    try (InstanceUpdateDtoDeserializer deserializer = new InstanceUpdateDtoDeserializer()) {
      deserializer.configure(
          Map.of(InstanceUpdateDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, "static-key-placeholder"),
          false);

      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, bytes))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Malformed")
          .hasMessageContaining(Constants.HEADER_ENGINE_SIGNATURE);
    }
  }

  @Test
  void deserialize_unknownKeyInSigningStore_throwsIllegalStateException() {
    KeyPair kp = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    InstanceUpdateDTO update = sampleUpdate();
    byte[] bytes = InstanceUpdateProtoMapper.toProto(update).toByteArray();
    SigningKeysStore store = mock(SigningKeysStore.class);
    when(store.getPublicKeyBase64("missing-key")).thenReturn(null);
    RecordHeaders headers = signedHeaders("missing-key", bytes, privateKeyBase64);

    try (InstanceUpdateDtoDeserializer deserializer = new InstanceUpdateDtoDeserializer()) {
      deserializer.setSigningKeysStore(store);

      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, bytes))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Unknown or revoked signing keyId='missing-key'");
    }
  }

  private static FlowNodeInstanceUpdateDTO sampleUpdate() {
    TaskInstanceDTO task = new TaskInstanceDTO();
    task.setState(ExecutionState.ACTIVE);
    task.setElementInstanceId(101L);
    task.setParentElementInstanceId(100L);
    task.setElementIndex(7);
    task.setElementId("service-task-a");
    task.setPassedCnt(2);
    task.setIncident(false);
    task.setIteration(true);
    task.setNextIterationId(102L);
    task.setInputElement(Variables.of(Map.of("input", "value")));
    task.setOutputElement(Variables.of(Map.of("result", 42L)));
    task.setLoopCnt(3);

    return new FlowNodeInstanceUpdateDTO(
        List.of(10L, 20L, 101L),
        task,
        VariablesDTO.of("approved", true, "total", 99L),
        1_713_000_001_234L,
        "flow-in-1",
        List.of("flow-out-1", "flow-out-2"));
  }

  private static RecordHeaders signedHeaders(
      String keyId, byte[] payload, String privateKeyBase64) {
    byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
    String sigHeader = keyId + "." + Base64.getEncoder().encodeToString(sig);
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, sigHeader.getBytes(StandardCharsets.UTF_8));
    return headers;
  }
}
