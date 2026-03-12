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

import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import io.taktx.serdes.JsonDeserializer;
import io.taktx.serdes.JsonSerializer;
import io.taktx.serdes.SigningSerializer;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JsonDeserializerTest {

  private static final String TOPIC = "test-topic";
  private static final String KEY_ID = "test-key-1";

  @AfterEach
  void tearDown() {
    SigningServiceHolder.clear();
  }

  @Test
  void testSerializeDeserializeUnsigned() {
    // Given
    try (JsonDeserializer<MyObject> deserializer =
            new JsonDeserializer<>(MyObject.class, false) {};
        JsonSerializer<MyObject> serializer = new JsonSerializer<>(MyObject.class) {}) {

      // When
      MyObject testValue = new MyObject("testValue");
      byte[] tests = serializer.serialize(null, testValue);
      MyObject result = deserializer.deserialize(null, tests);

      // Then
      assertThat(result).isEqualTo(testValue);
    }
  }

  @Test
  void testSerializeSignDeserializeVerify() {
    // Given – generate an Ed25519 key pair
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());

    // Register the signing function (mirrors what MessageSigningService does at engine startup)
    SigningServiceHolder.set(
        payload -> {
          byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
          return KEY_ID + "." + Base64.getEncoder().encodeToString(sig);
        });

    MyObject original = new MyObject("signedValue");

    try (JsonSerializer<MyObject> delegate = new JsonSerializer<>(MyObject.class) {};
        SigningSerializer<MyObject> signingSerializer = new SigningSerializer<>(delegate);
        JsonDeserializer<MyObject> deserializer =
            new JsonDeserializer<>(MyObject.class, false) {}) {

      // When – serialize and sign
      RecordHeaders headers = new RecordHeaders();
      byte[] signedBytes = signingSerializer.serialize(TOPIC, headers, original);

      // Configure the deserializer with the engine's public key so it can verify
      deserializer.configure(
          Map.of(JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);

      // Then – deserialization must succeed and the signature must be verified
      MyObject result = deserializer.deserialize(TOPIC, headers, signedBytes);
      assertThat(result).isEqualTo(original);
    }
  }

  @Test
  void testTamperedPayload_signatureVerificationFails() {
    // Given
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());

    SigningServiceHolder.set(
        payload -> {
          byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
          return KEY_ID + "." + Base64.getEncoder().encodeToString(sig);
        });

    MyObject original = new MyObject("tamperedValue");

    try (JsonSerializer<MyObject> delegate = new JsonSerializer<>(MyObject.class) {};
        SigningSerializer<MyObject> signingSerializer = new SigningSerializer<>(delegate);
        JsonDeserializer<MyObject> deserializer = new JsonDeserializer<>(MyObject.class, true) {}) {

      RecordHeaders headers = new RecordHeaders();
      byte[] signedBytes = signingSerializer.serialize(TOPIC, headers, original);

      // Tamper with the payload
      byte[] tampered = signedBytes.clone();
      tampered[signedBytes.length / 2] ^= 0xFF;

      deserializer.configure(
          Map.of(JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);

      // Then – verification must reject the tampered bytes
      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, tampered))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("signature");
    }
  }

  @Test
  void testSigningRequired_missingHeader_throws() {
    // Given
    KeyPair keyPair = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());

    MyObject original = new MyObject("requiresSignature");

    try (JsonSerializer<MyObject> delegate = new JsonSerializer<>(MyObject.class) {};
        JsonDeserializer<MyObject> deserializer = new JsonDeserializer<>(MyObject.class, true) {}) {

      byte[] bytes = delegate.serialize(TOPIC, original);

      // Configure with signing required but no signature in the headers
      deserializer.configure(
          Map.of(
              JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
              publicKeyBase64,
              JsonDeserializer.SIGNING_REQUIRED_CONFIG,
              "true"),
          false);

      // Then – missing signature header must be rejected
      assertThatThrownBy(() -> deserializer.deserialize(TOPIC, new RecordHeaders(), bytes))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no X-TaktX-Signature header");
    }
  }

  static class MyObject {

    private String field;

    public MyObject() {}

    public MyObject(String field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MyObject myObject = (MyObject) o;
      return Objects.equals(field, myObject.field);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(field);
    }

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }
  }
}
