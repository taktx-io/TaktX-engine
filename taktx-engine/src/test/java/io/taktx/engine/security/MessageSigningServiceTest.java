/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningIdentitySource;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.StaticSigningIdentitySource;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSigningServiceTest {

  private static final String KEY_ID = "test-key-1";
  private static final byte[] PAYLOAD = "cbor-payload-bytes".getBytes();

  private TaktConfiguration config;
  private GlobalConfigStore globalConfigStore;
  private MessageSigningService service;
  private SigningIdentitySource signingIdentitySource;

  private String publicKeyBase64;
  private KeyPair keyPair;

  @BeforeEach
  void setUp() {
    keyPair = SigningKeyGenerator.generate();
    publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    signingIdentitySource =
        new StaticSigningIdentitySource(
            SigningIdentity.ed25519(
                KEY_ID,
                SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
                publicKeyBase64));

    config = mock(TaktConfiguration.class);
    globalConfigStore = new GlobalConfigStore();
    service = new MessageSigningService(config, null, signingIdentitySource, false);
  }

  /**
   * Helper: build a service that reads from globalConfigStore and uses the static identity source.
   */
  private MessageSigningService serviceWithConfigStore(GlobalConfigStore store) {
    return new MessageSigningService(config, store, signingIdentitySource, false);
  }

  @Test
  void signingDisabled_locally_returnsNull() {
    assertThat(service.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigNull_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_but_globalConfigDisabled_returnsNull() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(false, false));
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNull();
  }

  @Test
  void signingEnabled_globalConfigPresent_returnsHeaderValue() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true, false));
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNotNull();
  }

  @Test
  void authorizationEnabled_evenWhenSigningDisabled_returnsHeaderValue() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(false, true));
    assertThat(svc.signToHeaderValue(PAYLOAD)).isNotNull();
  }

  @Test
  void headerValue_hasCorrectFormat_keyIdDotBase64() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true, false));

    String headerValue = svc.signToHeaderValue(PAYLOAD);

    assertThat(headerValue).startsWith(KEY_ID + ".");
    String b64Part = headerValue.substring(KEY_ID.length() + 1);
    assertThat(Base64.getDecoder().decode(b64Part)).hasSize(64);
  }

  @Test
  void headerValue_isVerifiableWithPublicKey() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true, false));

    String headerValue = svc.signToHeaderValue(PAYLOAD);
    byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(KEY_ID.length() + 1));

    assertThat(Ed25519Service.verify(PAYLOAD, sigBytes, publicKeyBase64)).isTrue();
  }

  @Test
  void multipleCallsWithSameKey_eachHasValidSignature() {
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);
    globalConfigStore.update(globalConfig(true, false));

    for (int i = 0; i < 5; i++) {
      byte[] payload = ("payload-" + i).getBytes();
      String hv = svc.signToHeaderValue(payload);
      assertThat(hv).isNotNull();
      byte[] sig = Base64.getDecoder().decode(hv.substring(KEY_ID.length() + 1));
      assertThat(Ed25519Service.verify(payload, sig, publicKeyBase64)).isTrue();
    }
  }

  // ── Key rotation ───────────────────────────────────────────────────────────

  @Test
  void keyRotation_switchToNewIdentity_signsWithNewKey() {
    // Start with key-1
    globalConfigStore.update(globalConfig(true, false));
    MessageSigningService svc = serviceWithConfigStore(globalConfigStore);

    String headerBefore = svc.signToHeaderValue(PAYLOAD);
    assertThat(headerBefore).startsWith(KEY_ID + ".");

    // Rotate to a new key-2
    KeyPair newKeyPair = SigningKeyGenerator.generate();
    String newPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(newKeyPair.getPublic());
    String newKeyId = "test-key-2";
    StaticSigningIdentitySource newSource =
        new StaticSigningIdentitySource(
            SigningIdentity.ed25519(
                newKeyId,
                SigningKeyGenerator.encodePrivateKey(newKeyPair.getPrivate()),
                newPublicKeyBase64));

    MessageSigningService rotatedSvc =
        new MessageSigningService(config, globalConfigStore, newSource, false);

    String headerAfter = rotatedSvc.signToHeaderValue(PAYLOAD);
    assertThat(headerAfter).isNotNull();
    assertThat(headerAfter).startsWith(newKeyId + ".");

    // Verify the new signature with the new public key
    byte[] newSig = Base64.getDecoder().decode(headerAfter.substring(newKeyId.length() + 1));
    assertThat(Ed25519Service.verify(PAYLOAD, newSig, newPublicKeyBase64)).isTrue();
  }

  @Test
  void keyRotation_previousIdentityCaptured_whenKeyIdChanges() {
    KeyPair key1 = SigningKeyGenerator.generate();
    KeyPair key2 = SigningKeyGenerator.generate();

    String key1Id = "rotation-key-1";
    String key2Id = "rotation-key-2";
    String key1Pub = SigningKeyGenerator.encodePublicKey(key1.getPublic());
    String key2Pub = SigningKeyGenerator.encodePublicKey(key2.getPublic());

    // Start with key1
    StaticSigningIdentitySource srcKey1 =
        new StaticSigningIdentitySource(
            SigningIdentity.ed25519(
                key1Id, SigningKeyGenerator.encodePrivateKey(key1.getPrivate()), key1Pub));

    MessageSigningService svc =
        new MessageSigningService(config, globalConfigStore, srcKey1, false);

    globalConfigStore.update(globalConfig(true, false));

    // Sign once to establish key1 as the active identity
    String h1 = svc.signToHeaderValue(PAYLOAD);
    assertThat(h1).startsWith(key1Id + ".");

    // Switch to key2
    StaticSigningIdentitySource srcKey2 =
        new StaticSigningIdentitySource(
            SigningIdentity.ed25519(
                key2Id, SigningKeyGenerator.encodePrivateKey(key2.getPrivate()), key2Pub));

    MessageSigningService svc2 =
        new MessageSigningService(config, globalConfigStore, srcKey2, false);

    // New service immediately uses key2
    String h2 = svc2.signToHeaderValue(PAYLOAD);
    assertThat(h2).isNotNull();
    assertThat(h2).startsWith(key2Id + ".");

    // key2 signature verifies with key2 public key
    byte[] sig2 = Base64.getDecoder().decode(h2.substring(key2Id.length() + 1));
    assertThat(Ed25519Service.verify(PAYLOAD, sig2, key2Pub)).isTrue();

    // key1 signature still verifies with key1 public key (backward compat)
    byte[] sig1 = Base64.getDecoder().decode(h1.substring(key1Id.length() + 1));
    assertThat(Ed25519Service.verify(PAYLOAD, sig1, key1Pub)).isTrue();
  }

  @Test
  void getKeyId_returnsCurrentKeyId() {
    assertThat(service.getKeyId()).isEqualTo(KEY_ID);
  }

  @Test
  void getPublicKeyBase64_returnsCurrentPublicKey() {
    assertThat(service.getPublicKeyBase64()).isEqualTo(publicKeyBase64);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private GlobalConfigurationDTO globalConfig(
      boolean signingEnabled, boolean engineRequiresAuthorization) {
    return GlobalConfigurationDTO.builder()
        .signingEnabled(signingEnabled)
        .engineRequiresAuthorization(engineRequiresAuthorization)
        .trustedKeyIds(List.of(KEY_ID))
        .build();
  }
}
