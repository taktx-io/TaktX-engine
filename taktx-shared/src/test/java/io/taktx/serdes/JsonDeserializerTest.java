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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.Constants;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.security.Ed25519Service;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningKeyGenerator;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JsonDeserializerTest {

  private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
  }

  // Concrete subclass for testing
  static class NoSignValidatingDeserializer extends JsonDeserializer<ProcessDefinitionKey> {
    NoSignValidatingDeserializer() {
      super(ProcessDefinitionKey.class, false);
    }
  }

  // Concrete subclass for testing
  static class SignValidatingDeserializer extends JsonDeserializer<ProcessDefinitionKey> {
    SignValidatingDeserializer() {
      super(ProcessDefinitionKey.class, true);
    }
  }

  private byte[] cbor(Object obj) throws Exception {
    return CBOR.writeValueAsBytes(obj);
  }

  // ── isKey=true: signature header must be ignored ─────────────────────────

  @Test
  void keyDeserializer_signatureHeaderPresent_isIgnored() throws Exception {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());

    ProcessDefinitionKey key = new ProcessDefinitionKey("my-proc", 1);
    byte[] keyBytes = cbor(key);

    // Build a deliberately wrong signature (signed over something else) to prove it is not checked
    byte[] valueBytes = cbor("some-value");
    byte[] sig = Ed25519Service.sign(valueBytes, privateKeyBase64);
    String sigHeader = "test-key." + Base64.getEncoder().encodeToString(sig);

    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, sigHeader.getBytes());

    NoSignValidatingDeserializer deser = new NoSignValidatingDeserializer();
    // Configure as key deserializer (isKey=true) with a static key source present
    deser.configure(Map.of(JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), true);

    // Must NOT throw even though the signature would fail if checked against keyBytes
    ProcessDefinitionKey result = deser.deserialize("test-topic", headers, keyBytes);
    assertThat(result.getProcessDefinitionId()).isEqualTo("my-proc");
  }

  // ── isKey=false: signature header is verified against value bytes ─────────

  @Test
  void valueDeserializer_correctSignature_passes() throws Exception {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());

    ProcessDefinitionKey value = new ProcessDefinitionKey("my-proc", 1);
    byte[] valueBytes = cbor(value);

    byte[] sig = Ed25519Service.sign(valueBytes, privateKeyBase64);
    String sigHeader = "test-key." + Base64.getEncoder().encodeToString(sig);

    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, sigHeader.getBytes());

    NoSignValidatingDeserializer deser = new NoSignValidatingDeserializer();
    deser.configure(Map.of(JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);

    ProcessDefinitionKey result = deser.deserialize("test-topic", headers, valueBytes);
    assertThat(result.getProcessDefinitionId()).isEqualTo("my-proc");
  }

  @Test
  void valueDeserializer_wrongSignature_throws() throws Exception {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());
    KeyPair otherKp = SigningKeyGenerator.generate();
    String otherPrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(otherKp.getPrivate());

    ProcessDefinitionKey value = new ProcessDefinitionKey("my-proc", 1);
    byte[] valueBytes = cbor(value);

    // Sign with a different key — verification must fail
    byte[] sig = Ed25519Service.sign(valueBytes, otherPrivateKeyBase64);
    String sigHeader = "test-key." + Base64.getEncoder().encodeToString(sig);

    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, sigHeader.getBytes());

    SignValidatingDeserializer deser = new SignValidatingDeserializer();
    deser.configure(Map.of(JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);

    assertThatThrownBy(() -> deser.deserialize("test-topic", headers, valueBytes))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("signature verification failed");
  }

  // ── isKey=false, signingRequired=true: missing header rejects ────────────

  @Test
  void valueDeserializer_signingRequired_noHeader_throws() throws Exception {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());

    ProcessDefinitionKey value = new ProcessDefinitionKey("my-proc", 1);
    byte[] valueBytes = cbor(value);

    SignValidatingDeserializer deser = new SignValidatingDeserializer();
    deser.configure(
        Map.of(
            JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
            publicKeyBase64,
            JsonDeserializer.SIGNING_REQUIRED_CONFIG,
            "true"),
        false);

    assertThatThrownBy(() -> deser.deserialize("test-topic", new RecordHeaders(), valueBytes))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no X-TaktX-Signature header");
  }

  // ── isKey=true, signingRequired=true: missing header still passes ─────────

  @Test
  void keyDeserializer_signingRequired_noHeader_passes() throws Exception {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());

    ProcessDefinitionKey key = new ProcessDefinitionKey("my-proc", 1);
    byte[] keyBytes = cbor(key);

    NoSignValidatingDeserializer deser = new NoSignValidatingDeserializer();
    deser.configure(
        Map.of(
            JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
            publicKeyBase64,
            JsonDeserializer.SIGNING_REQUIRED_CONFIG,
            "true"),
        true);

    // Key deserializer must never enforce the signature header
    ProcessDefinitionKey result = deser.deserialize("test-topic", new RecordHeaders(), keyBytes);
    assertThat(result.getProcessDefinitionId()).isEqualTo("my-proc");
  }

  @Test
  void valueDeserializer_runtimeSigningFlagCanToggleWithoutReconfigure() throws Exception {
    KeyPair kp = SigningKeyGenerator.generate();
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());

    ProcessDefinitionKey value = new ProcessDefinitionKey("my-proc", 1);
    byte[] valueBytes = cbor(value);

    SignValidatingDeserializer deser = new SignValidatingDeserializer();
    deser.configure(Map.of(JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG, publicKeyBase64), false);

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(false).build());
    ProcessDefinitionKey unsignedOk =
        deser.deserialize("test-topic", new RecordHeaders(), valueBytes);
    assertThat(unsignedOk.getProcessDefinitionId()).isEqualTo("my-proc");

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    assertThatThrownBy(() -> deser.deserialize("test-topic", new RecordHeaders(), valueBytes))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no X-TaktX-Signature header");
  }
}
