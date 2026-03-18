/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.client.serdes.ProcessInstanceTriggerSerializer;
import io.taktx.dto.Constants;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.SigningKeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProcessInstanceTriggerEnvelopeDeserializerTest {

  private static final String TOPIC = "default.process-instance-trigger";

  private final ProcessInstanceTriggerEnvelopeDeserializer deserializer =
      new ProcessInstanceTriggerEnvelopeDeserializer();

  @AfterEach
  void tearDown() {
    EngineSigningKeysHolder.clear();
  }

  @Test
  void malformedSignatureHeader_returnsEnvelopeWithSignatureError() {
    byte[] payload = serialize(startCommand());
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, "bad-header".getBytes(StandardCharsets.UTF_8));

    ProcessInstanceTriggerEnvelope envelope = deserializer.deserialize(TOPIC, headers, payload);

    assertThat(envelope.trigger()).isInstanceOf(StartCommandDTO.class);
    assertThat(envelope.signatureVerified()).isFalse();
    assertThat(envelope.signatureKeyId()).isNull();
    assertThat(envelope.hasSignatureError()).isTrue();
    assertThat(envelope.signatureError()).contains("Malformed X-TaktX-Signature header");
  }

  @Test
  void malformedBase64Signature_returnsEnvelopeWithSignatureError() {
    StartCommandDTO trigger = startCommand();
    byte[] payload = serialize(trigger);
    String keyId = "worker-key";
    KeyPair keyPair = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    EngineSigningKeysHolder.set(
        resolvedKeyId -> keyId.equals(resolvedKeyId) ? publicKeyBase64 : null);

    RecordHeaders headers = new RecordHeaders();
    headers.add(
        Constants.HEADER_ENGINE_SIGNATURE,
        (keyId + ".%%%not-base64%%%").getBytes(StandardCharsets.UTF_8));

    ProcessInstanceTriggerEnvelope envelope = deserializer.deserialize(TOPIC, headers, payload);

    assertThat(envelope.trigger()).isEqualTo(trigger);
    assertThat(envelope.signatureVerified()).isFalse();
    assertThat(envelope.signatureKeyId()).isEqualTo(keyId);
    assertThat(envelope.hasSignatureError()).isTrue();
    assertThat(envelope.signatureError()).contains("Malformed base64 signature");
  }

  @Test
  void unknownKeyId_returnsEnvelopeWithSignatureError() {
    byte[] payload = serialize(startCommand());
    EngineSigningKeysHolder.set(ignored -> null);

    RecordHeaders headers = new RecordHeaders();
    headers.add(
        Constants.HEADER_ENGINE_SIGNATURE, "missing-key.AABB".getBytes(StandardCharsets.UTF_8));

    ProcessInstanceTriggerEnvelope envelope = deserializer.deserialize(TOPIC, headers, payload);

    assertThat(envelope.signatureVerified()).isFalse();
    assertThat(envelope.signatureKeyId()).isEqualTo("missing-key");
    assertThat(envelope.hasSignatureError()).isTrue();
    assertThat(envelope.signatureError())
        .contains("Unknown or revoked signing keyId='missing-key'");
  }

  @Test
  void invalidSignature_returnsEnvelopeWithSignatureError() {
    StartCommandDTO trigger = startCommand();
    byte[] payload = serialize(trigger);
    String keyId = "worker-key";
    KeyPair keyPair = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    EngineSigningKeysHolder.set(
        resolvedKeyId -> keyId.equals(resolvedKeyId) ? publicKeyBase64 : null);

    byte[] bogusSignature = new byte[64];
    RecordHeaders headers = new RecordHeaders();
    headers.add(
        Constants.HEADER_ENGINE_SIGNATURE,
        (keyId + "." + Base64.getEncoder().encodeToString(bogusSignature))
            .getBytes(StandardCharsets.UTF_8));

    ProcessInstanceTriggerEnvelope envelope = deserializer.deserialize(TOPIC, headers, payload);

    assertThat(envelope.trigger()).isEqualTo(trigger);
    assertThat(envelope.signatureVerified()).isFalse();
    assertThat(envelope.signatureKeyId()).isEqualTo(keyId);
    assertThat(envelope.hasSignatureError()).isTrue();
    assertThat(envelope.signatureError())
        .contains("Engine Ed25519 signature verification failed for keyId=" + keyId);
  }

  @Test
  void validSignature_returnsVerifiedEnvelope() {
    StartCommandDTO trigger = startCommand();
    byte[] payload = serialize(trigger);
    String keyId = "worker-key";
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    EngineSigningKeysHolder.set(
        resolvedKeyId -> keyId.equals(resolvedKeyId) ? publicKeyBase64 : null);

    byte[] signature = Ed25519Service.sign(payload, privateKeyBase64);
    RecordHeaders headers = new RecordHeaders();
    headers.add(
        Constants.HEADER_ENGINE_SIGNATURE,
        (keyId + "." + Base64.getEncoder().encodeToString(signature))
            .getBytes(StandardCharsets.UTF_8));

    ProcessInstanceTriggerEnvelope envelope = deserializer.deserialize(TOPIC, headers, payload);

    assertThat(envelope.trigger()).isEqualTo(trigger);
    assertThat(envelope.signatureVerified()).isTrue();
    assertThat(envelope.signatureKeyId()).isEqualTo(keyId);
    assertThat(envelope.hasSignatureError()).isFalse();
    assertThat(envelope.signatureError()).isNull();
  }

  private byte[] serialize(StartCommandDTO trigger) {
    try (ProcessInstanceTriggerSerializer serializer = new ProcessInstanceTriggerSerializer()) {
      return serializer.serialize(TOPIC, trigger);
    }
  }

  private StartCommandDTO startCommand() {
    return new StartCommandDTO(
        UUID.randomUUID(), null, null, new ProcessDefinitionKey("proc", -1), VariablesDTO.empty());
  }
}
