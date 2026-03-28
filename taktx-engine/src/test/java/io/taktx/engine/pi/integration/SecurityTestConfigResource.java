/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.taktx.engine.license.LicenseManager;
import io.taktx.engine.security.MessageSigningService;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

/**
 * Quarkus test resource that provisions RSA test keys for JWT validation and captures the engine's
 * active Ed25519 engine signing key after Quarkus startup.
 *
 * <p>The generated/observed public keys are exposed via static fields so tests can produce valid
 * JWTs and verify Ed25519 signatures without any shared mutable state.
 *
 * <p>The RSA public key is NOT injected via {@code taktx.platform.public-key} — instead, the test
 * publishes it to the {@code taktx-signing-keys} KTable under {@link #PLATFORM_KID} in
 * {@code @BeforeAll}, matching the {@code kid} header the JWT builder sets. The engine resolves the
 * key at validation time from the KTable, just like the real platform does.
 */
public class SecurityTestConfigResource implements QuarkusTestResourceLifecycleManager {

  /** Key ID used as JWT {@code kid} header and as the KTable key for the RSA public key. */
  static final String PLATFORM_KID = "platform-test-key";

  /** RSA key-pair generated once per test-suite run, used for RS256 JWT signing. */
  static java.security.PublicKey rsaPublicKey;

  static java.security.PrivateKey rsaPrivateKey;

  /** Base64-encoded RSA public key — exposed so tests can publish it to the signing-keys topic. */
  static String rsaPublicKeyBase64;

  /** Base64-encoded engine public key — exposed for the test client's JsonDeserializer. */
  static String enginePublicKeyBase64;

  /** Active engine signing key ID, exposed for assertions. */
  static String engineKeyId;

  @Override
  public Map<String, String> start() {
    try {
      // ── RSA key-pair for command authorization ────────────────────────────
      java.security.KeyPair rsaKp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      rsaPublicKey = rsaKp.getPublic();
      rsaPrivateKey = rsaKp.getPrivate();
      rsaPublicKeyBase64 = Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded());

      return Map.of("taktx.test", "true", "kafka.devservices.auto-create-topics", "false");
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test keys", e);
    }
  }

  /**
   * Called after Quarkus has started. Uses the Arc CDI container to look up the {@link
   * LicenseManager} bean and push a test license so partition-budget enforcement uses unlimited
   * partitions in tests.
   */
  @Override
  public void inject(TestInjector testInjector) {
    try (var handle = Arc.container().instance(LicenseManager.class)) {
      LicenseManager licenseManager = handle.get();
      if (licenseManager != null) {
        licenseManager.updateFromLicensePush("TEST", null);
      }
    }

    refreshEngineSigningMetadata();
  }

  static void refreshEngineSigningMetadata() {
    try (var handle = Arc.container().instance(MessageSigningService.class)) {
      MessageSigningService signingService = handle.get();
      if (signingService != null) {
        enginePublicKeyBase64 = signingService.getPublicKeyBase64();
        engineKeyId = signingService.getKeyId();
      }
    }
    if (enginePublicKeyBase64 == null || engineKeyId == null) {
      throw new IllegalStateException(
          "MessageSigningService did not expose an active engine signing key");
    }
  }

  @Override
  public void stop() {
    enginePublicKeyBase64 = null;
    engineKeyId = null;
  }
}
