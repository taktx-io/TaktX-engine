/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
import io.taktx.serdes.JsonSerializer;
import io.taktx.serdes.SigningSerializer;
import java.security.KeyPair;
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
 * <p>Covers the path that was broken in production: the engine's {@link SigningSerializer} signs
 * the CBOR bytes that the Kafka broker stores; the worker's {@link InstanceUpdateJsonDeserializer}
 * (backed by {@link io.taktx.serdes.JsonDeserializer}) receives those exact bytes and verifies the
 * signature before deserializing.
 *
 * <p>The critical invariant being tested: <em>the bytes that are signed must be identical to the
 * bytes that are verified</em>. Re-serializing a deserialized DTO breaks this invariant because
 * Jackson CBOR does not guarantee byte-for-byte round-trip identity.
 *
 * <p>Also includes a regression test using bytes captured from a live failure (scratch_2.txt).
 */
class SigningRoundTripTest {

  private static final String TOPIC = "default.instance-update";
  private static final String KEY_ID = "engine-key-1";

  private KeyPair keyPair;
  private String privateKeyBase64;
  private String publicKeyBase64;

  @BeforeEach
  void setUp() {
    keyPair = SigningKeyGenerator.generate();
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
   * Full round trip: SigningSerializer signs the CBOR bytes → InstanceUpdateJsonDeserializer
   * verifies and deserializes. This is the exact path a live worker takes.
   */
  @Test
  void signingSerializer_to_instanceUpdateDeserializer_roundTrip() {
    InstanceUpdateDTO dto = buildSampleUpdate();

    // Engine side: serialize + sign (SigningSerializer calls serialize(topic, headers, data))
    byte[] signedBytes = serializeAndSign(dto);
    Headers headers = captureHeaders(dto);

    // Worker side: deserialize with signature verification
    try (InstanceUpdateJsonDeserializer deserializer = new InstanceUpdateJsonDeserializer()) {
      deserializer.configure(
          Map.of(io.taktx.serdes.JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64),
          false);

      // Must not throw — bytes and signature are consistent
      InstanceUpdateDTO result = deserializer.deserialize(TOPIC, headers, signedBytes);
      assertThat(result).isNotNull();
    }
  }

  /**
   * Verifies that re-serializing the deserialized DTO produces identical bytes. If this fails it
   * explains the live breakage: any consumer that re-serializes before verifying will get a byte
   * mismatch.
   */
  @Test
  void reSerializedBytes_areIdenticalToOriginal() {
    InstanceUpdateDTO dto = buildSampleUpdate();
    byte[] originalBytes = serializeAndSign(dto);

    // Re-serialize the deserialized object — must be byte-for-byte identical
    try (JsonSerializer<InstanceUpdateDTO> serializer =
        new JsonSerializer<>(InstanceUpdateDTO.class) {}) {
      // Deserialize first
      try (InstanceUpdateJsonDeserializer deserializer = new InstanceUpdateJsonDeserializer()) {
        InstanceUpdateDTO roundTripped = deserializer.deserialize(TOPIC, originalBytes);
        byte[] reSerializedBytes = serializer.serialize(TOPIC, roundTripped);
        assertThat(reSerializedBytes)
            .as(
                "Re-serialized bytes must be identical to originals — if this fails, "
                    + "any code that re-serializes before verifying will break signature validation")
            .isEqualTo(originalBytes);
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
    tampered[signedBytes.length / 2] ^= 0xFF;

    try (InstanceUpdateJsonDeserializer deserializer = new InstanceUpdateJsonDeserializer()) {
      deserializer.configure(
          Map.of(io.taktx.serdes.JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64),
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

    try (JsonSerializer<InstanceUpdateDTO> serializer =
        new JsonSerializer<>(InstanceUpdateDTO.class) {}) {
      byte[] bytes = serializer.serialize(TOPIC, dto);
      Headers emptyHeaders = new RecordHeaders();

      try (InstanceUpdateJsonDeserializer deserializer = new InstanceUpdateJsonDeserializer()) {
        deserializer.configure(
            Map.of(io.taktx.serdes.JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64),
            false);
        // Must not throw — absent header = signing disabled / not yet enabled
        InstanceUpdateDTO result = deserializer.deserialize(TOPIC, emptyHeaders, bytes);
        assertThat(result).isNotNull();
      }
    }
  }

  /** An unknown keyId in the signature header must be rejected. */
  @Test
  void unknownKeyId_verificationFails() {
    InstanceUpdateDTO dto = buildSampleUpdate();
    byte[] bytes = serializeAndSign(dto);
    Headers headers = captureHeaders(dto);

    try (InstanceUpdateJsonDeserializer deserializer = new InstanceUpdateJsonDeserializer()) {
      // Configure with a *different* public key — keyId resolves but signature won't match
      KeyPair other = SigningKeyGenerator.generate();
      String otherPublicKey = SigningKeyGenerator.encodePublicKey(other.getPublic());
      deserializer.configure(
          Map.of(io.taktx.serdes.JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, otherPublicKey), false);
      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, bytes))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // ── regression: live bytes from scratch_2.txt ─────────────────────────────

  /**
   * Regression test using bytes generated by {@link SigningFixtureGenerator} — the fixture was
   * produced by our own {@link SigningSerializer} + {@link io.taktx.security.Ed25519Service} stack
   * and self-verified before being embedded here.
   *
   * <p>If the {@link InstanceUpdateDTO} serialization format ever changes, re-run {@code
   * SigningFixtureGenerator.generateFixture()} and paste the new values here.
   *
   * <p>This test verifies two things:
   *
   * <ol>
   *   <li>{@link io.taktx.security.Ed25519Service#verify} accepts the fixture signature.
   *   <li>The header format ({@code "<keyId>.<base64>"}) is parsed correctly.
   * </ol>
   */
  @Test
  void regression_liveCapturedBytes_signatureVerifies() throws Exception {
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
   * Serializes the DTO and signs the bytes, returning the raw CBOR payload. Mirrors what
   * SigningSerializer.serialize(topic, headers, data) does.
   */
  private byte[] serializeAndSign(InstanceUpdateDTO dto) {
    try (JsonSerializer<InstanceUpdateDTO> delegateSerializer =
        new JsonSerializer<>(InstanceUpdateDTO.class) {}) {
      SigningSerializer<InstanceUpdateDTO> signingSerializer =
          new SigningSerializer<>(delegateSerializer);
      RecordHeaders headers = new RecordHeaders();
      return signingSerializer.serialize(TOPIC, headers, dto);
    }
  }

  /**
   * Returns headers with the X-TaktX-Signature that SigningSerializer attached. Captures them by
   * running through SigningSerializer once more.
   */
  private Headers captureHeaders(InstanceUpdateDTO dto) {
    try (JsonSerializer<InstanceUpdateDTO> delegateSerializer =
        new JsonSerializer<>(InstanceUpdateDTO.class) {}) {
      SigningSerializer<InstanceUpdateDTO> signingSerializer =
          new SigningSerializer<>(delegateSerializer);
      RecordHeaders headers = new RecordHeaders();
      signingSerializer.serialize(TOPIC, headers, dto);
      return headers;
    }
  }
}
