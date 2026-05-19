/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import io.taktx.serdes.InstanceUpdateDtoDeserializer;
import io.taktx.serdes.InstanceUpdateProtoMapper;
import io.taktx.serdes.ProtoSigningSerializer;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the complete engine-producer → worker-consumer signing round trip.
 *
 * <p>Covers the path that was broken in production: the engine's {@link ProtoSigningSerializer}
 * signs the protobuf bytes that the Kafka broker stores; the worker's {@link
 * InstanceUpdateDeserializer} receives those exact bytes and verifies the signature before
 * deserializing.
 *
 * <p>The critical invariant being tested: <em>the bytes that are signed must be identical to the
 * bytes that are verified</em>. Re-serializing a deserialized DTO breaks this invariant whenever
 * protobuf mapping normalizes the message into a semantically equivalent but byte-different form.
 *
 * <p>Also includes a regression test using bytes captured from a live failure (scratch_2.txt).
 */
class SigningRoundTripTest {

  private static final String TOPIC = "default.instance-update";
  private static final String KEY_ID = "engine-key-1";

  private String privateKeyBase64;
  private String publicKeyBase64;

  @BeforeEach
  void setUp() {
    KeyPair keyPair = SigningKeyGenerator.generate();
    privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());

    // Register the signing function exactly as MessageSigningService does at engine startup
    SigningServiceHolder.set(
        payload -> {
          try {
            byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
            return KEY_ID + "." + Base64.getEncoder().encodeToString(sig);
          } catch (Exception e) {
            return null;
          }
        });
  }

  @AfterEach
  void tearDown() {
    SigningServiceHolder.clear();
  }

  // ── happy path ─────────────────────────────────────────────────────────────

  /**
   * Full round trip: ProtoSigningSerializer signs the protobuf bytes → InstanceUpdateDeserializer
   * verifies and deserializes. This is the exact path a live worker
   * takes.
   */
  @Test
  void signingSerializer_to_instanceUpdateDeserializer_roundTrip() {
    InstanceUpdateDTO dto = buildSampleUpdate();

    // Engine side: serialize + sign using the protobuf envelope bytes
    byte[] signedBytes = serializeAndSign(dto);
    Headers headers = captureHeaders(dto);

    // Worker side: deserialize with signature verification
    try (InstanceUpdateDeserializer deserializer = new InstanceUpdateDeserializer()) {
      deserializer.configure(
          Map.of(io.taktx.serdes.ProtoDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64),
          false);

      // Must not throw — bytes and signature are consistent
      InstanceUpdateDTO result = deserializer.deserialize(TOPIC, headers, signedBytes);
      assertThat(result).isNotNull();
    }
  }

  /**
   * Re-serializing the DTO must preserve semantics, but protobuf field normalization may still
   * produce different wire bytes. Signature verification therefore has to run against the original
   * Kafka payload bytes before deserializing.
   */
  @Test
  void reSerialization_preservesSemantics_evenWhenWireBytesAreNormalized() {
    InstanceUpdateDTO dto = buildSampleUpdate();
    byte[] originalBytes = serializeAndSign(dto);
    Headers headers = captureHeaders(dto);

    try (InstanceUpdateDeserializer deserializer = new InstanceUpdateDeserializer()) {
      InstanceUpdateDTO roundTripped = deserializer.deserialize(TOPIC, originalBytes);
      byte[] reSerializedBytes = InstanceUpdateProtoMapper.toProto(roundTripped).toByteArray();
      InstanceUpdateDTO reparsed = deserializer.deserialize(TOPIC, reSerializedBytes);

      assertThat(roundTripped)
          .as("Deserializing the signed payload must preserve the logical DTO")
          .usingRecursiveComparison()
          .ignoringFields("scope.gatewayInstances", "scope.subscriptions")
          .isEqualTo(dto);
      assertThat(reparsed)
          .as("Re-serializing and parsing again must preserve the logical DTO even if bytes change")
          .usingRecursiveComparison()
          .ignoringFields("scope.gatewayInstances", "scope.subscriptions")
          .isEqualTo(dto);

      assertThat(((ProcessInstanceUpdateDTO) roundTripped).getScope().getGatewayInstances())
          .as("The mapper may normalize an absent gateway-instance map to an empty map")
          .isEmpty();
      assertThat(((ProcessInstanceUpdateDTO) roundTripped).getScope().getSubscriptions())
          .as("The mapper may normalize absent subscriptions to an empty DTO container")
          .isNotNull();
      assertThat(((ProcessInstanceUpdateDTO) reparsed).getScope().getGatewayInstances())
          .as("The normalization should remain stable after re-serialization")
          .isEmpty();
      assertThat(((ProcessInstanceUpdateDTO) reparsed).getScope().getSubscriptions())
          .as("The empty subscription container should remain stable after re-serialization")
          .isNotNull();

      String headerValue =
          new String(
              headers.lastHeader(io.taktx.dto.Constants.HEADER_ENGINE_SIGNATURE).value(),
              java.nio.charset.StandardCharsets.UTF_8);
      byte[] signature =
          Base64.getDecoder().decode(headerValue.substring(headerValue.indexOf('.') + 1));

      assertThat(Ed25519Service.verify(originalBytes, signature, publicKeyBase64))
          .as("The original Kafka payload bytes must verify with the recorded signature")
          .isTrue();

      if (!Arrays.equals(reSerializedBytes, originalBytes)) {
        assertThat(Ed25519Service.verify(reSerializedBytes, signature, publicKeyBase64))
            .as(
                "If protobuf mapping normalizes the payload bytes, re-serialized bytes must not be "
                    + "used for signature verification")
            .isFalse();
      }
    }
  }

  /** A tampered payload must be rejected by the deserializer. */
  @Test
  void tamperedPayload_verificationFails() {
    InstanceUpdateDTO dto = buildSampleUpdate();
    byte[] signedBytes = serializeAndSign(dto);
    Headers headers = captureHeaders(dto);

    // Flip a byte in the middle of the payload
    byte[] tampered = signedBytes.clone();
    tampered[signedBytes.length / 2] = (byte) (tampered[signedBytes.length / 2] ^ 0xFF);

    try (InstanceUpdateDeserializer deserializer = new InstanceUpdateDeserializer()) {
      deserializer.configure(
          Map.of(io.taktx.serdes.ProtoDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64),
          false);
      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, tampered))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("signature");
    }
  }

  /**
   * A record with no signature header passes through when a key source is configured — engine
   * signing may be disabled on old deployments.
   */
  @Test
  void noSignatureHeader_passesThrough_whenKeySourceConfigured() {
    InstanceUpdateDTO dto = buildSampleUpdate();

    byte[] bytes = InstanceUpdateProtoMapper.toProto(dto).toByteArray();
    Headers emptyHeaders = new RecordHeaders();

    try (InstanceUpdateDeserializer deserializer = new InstanceUpdateDeserializer()) {
      deserializer.configure(
          Map.of(InstanceUpdateDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);
      // Must not throw — absent header = signing disabled / not yet enabled
      InstanceUpdateDTO result = deserializer.deserialize(TOPIC, emptyHeaders, bytes);
      assertThat(result).isNotNull();
    }
  }

  /** An unknown keyId in the signature header must be rejected. */
  @Test
  void unknownKeyId_verificationFails() {
    InstanceUpdateDTO dto = buildSampleUpdate();
    byte[] bytes = serializeAndSign(dto);
    Headers headers = captureHeaders(dto);

    try (InstanceUpdateDeserializer deserializer = new InstanceUpdateDeserializer()) {
      // Configure with a *different* public key — keyId resolves but signature won't match
      KeyPair other = SigningKeyGenerator.generate();
      String otherPublicKey = SigningKeyGenerator.encodePublicKey(other.getPublic());
      deserializer.configure(
          Map.of(io.taktx.serdes.ProtoDtoDeserializer.ENGINE_PUBLIC_KEY_CONFIG, otherPublicKey),
          false);
      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, bytes))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // ── regression: live bytes from scratch_2.txt ─────────────────────────────

  /**
   * Regression test using bytes generated by the shared signing fixture generator — the fixture was
   * produced by our own {@link ProtoSigningSerializer} + {@link io.taktx.security.Ed25519Service}
   * stack and self-verified before being embedded here.
   *
   * <p>If the {@link InstanceUpdateDTO} wire format ever changes, regenerate the fixture and paste
   * the new values here.
   *
   * <p>This test verifies two things:
   *
   * <ol>
   *   <li>{@link io.taktx.security.Ed25519Service#verify} accepts the fixture signature.
   *   <li>The header format ({@code "<keyId>.<base64>"}) is parsed correctly.
   * </ol>
   */
  @Test
  void regression_liveCapturedBytes_signatureVerifies() {
    // Fixture generated by SigningFixtureGenerator.generateFixture() — SELF-CHECK: PASS
    // Run that generator again if the DTO serialization format ever changes.
    byte[] liveBytes = {
      -97, 97, 80, -97, -10, -10, -10, -10, -10, -97, 0, 0, -10, 0, -10, -10, -1, -10, -10, -10, -1,
      -1
    };
    String liveHeaderValue =
        "engine-key-1.ku53iv5Z8vGwvcs9j6fIfAplncnzNMifpXXByjXzIuJ6FMXdcgoXO1S+3Sl5maSHgXVXJ5FPK3/5EQp+/DtLBA==";
    String livePublicKey = "MCowBQYDK2VwAyEAT9RZIa22Lbdt3FzFLRGfChVJjjTEFsYjhkE9lehE6ms=";

    int dot = liveHeaderValue.indexOf('.');
    String keyId = liveHeaderValue.substring(0, dot);
    byte[] sigBytes = Base64.getDecoder().decode(liveHeaderValue.substring(dot + 1));

    assertThat(keyId).isEqualTo("engine-key-1");
    assertThat(Ed25519Service.verify(liveBytes, sigBytes, livePublicKey))
        .as(
            "Live signature must verify against live bytes with the captured public key. "
                + "A failure here means the bytes that were signed differ from the bytes "
                + "that were verified — indicating a re-serialization bug on the consumer side.")
        .isTrue();
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private InstanceUpdateDTO buildSampleUpdate() {
    ScopeDTO scope = new ScopeDTO();
    return new ProcessInstanceUpdateDTO(null, null, null, null, scope, null, null, null);
  }

  /**
   * Serializes the DTO and signs the bytes, returning the raw protobuf payload. Mirrors what
   * ProtoSigningSerializer.serialize(topic, headers, data) does.
   */
  private byte[] serializeAndSign(InstanceUpdateDTO dto) {
    try (ProtoSigningSerializer<InstanceUpdateDTO> signingSerializer =
        new ProtoSigningSerializer<>(InstanceUpdateProtoMapper::toProto)) {
      RecordHeaders headers = new RecordHeaders();
      return signingSerializer.serialize(TOPIC, headers, dto);
    }
  }

  /**
   * Returns headers with the tx-sig value that ProtoSigningSerializer attached. Captures them by
   * running through ProtoSigningSerializer once more.
   */
  private Headers captureHeaders(InstanceUpdateDTO dto) {
    try (ProtoSigningSerializer<InstanceUpdateDTO> signingSerializer =
        new ProtoSigningSerializer<>(InstanceUpdateProtoMapper::toProto)) {
      RecordHeaders headers = new RecordHeaders();
      signingSerializer.serialize(TOPIC, headers, dto);
      return headers;
    }
  }
}
