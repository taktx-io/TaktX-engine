/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.pi.integration;

import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.taktx.engine.license.LicenseManager;
import io.taktx.security.SigningKeyGenerator;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

/**
 * Quarkus test resource that enables both authorization and signing, injects a freshly generated
 * RSA key-pair (for JWT validation) and an Ed25519 key-pair (for message signing) into the engine
 * configuration.
 *
 * <p>The generated public keys are exposed via static accessors so that tests can produce valid
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

  /** Ed25519 key-pair generated once per test-suite run, used for Ed25519 message signing. */
  static java.security.PublicKey ed25519PublicKey;

  /** Base64-encoded engine public key — exposed for the test client's JsonDeserializer. */
  static String ed25519PublicKeyBase64;

  @Override
  public Map<String, String> start() {
    try {
      // ── RSA key-pair for command authorization ────────────────────────────
      KeyPair rsaKp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      rsaPublicKey = rsaKp.getPublic();
      rsaPrivateKey = rsaKp.getPrivate();
      rsaPublicKeyBase64 = Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded());

      // ── Ed25519 key-pair for engine-internal message signing ──────────────
      KeyPair ed25519Kp = SigningKeyGenerator.generate();
      ed25519PublicKey = ed25519Kp.getPublic();
      ed25519PublicKeyBase64 = SigningKeyGenerator.encodePublicKey(ed25519Kp.getPublic());
      String ed25519PrivateBase64 = SigningKeyGenerator.encodePrivateKey(ed25519Kp.getPrivate());

      // Engine signing key — keyId "test-key-1"
      System.setProperty("taktx.signing.private-key", ed25519PrivateBase64);
      System.setProperty("taktx.signing.public-key", ed25519PublicKeyBase64);
      System.setProperty("taktx.signing.key-id", "test-key-1");

      return Map.of(
          "taktx.test", "true",
          "kafka.devservices.auto-create-topics", "false",
          "taktx.security.authorization.enabled", "true",
          "taktx.security.authorization.nonce-check.enabled", "true",
          "taktx.security.signing.enabled", "true",
          "taktx.signing.private-key", ed25519PrivateBase64,
          "taktx.signing.public-key", ed25519PublicKeyBase64);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test keys", e);
    }
  }

  /**
   * Called after Quarkus has started. Uses the Arc CDI container to look up the {@link
   * LicenseManager} bean and grant both security features, so that {@link
   * io.taktx.engine.license.DefaultLicenseManager} enforces authorization and signing without
   * requiring a real signed License3j file in tests.
   */
  @Override
  public void inject(TestInjector testInjector) {
    try (var handle = Arc.container().instance(LicenseManager.class)) {
      LicenseManager licenseManager = handle.get();
      if (licenseManager != null) {
        licenseManager.updateFromLicensePush(
            "TEST", null, /* eventSigning= */ true, /* commandAuthorization= */ true);
      }
    }
  }

  @Override
  public void stop() {
    System.clearProperty("taktx.signing.private-key");
    System.clearProperty("taktx.signing.public-key");
    System.clearProperty("taktx.signing.key-id");
  }
}
