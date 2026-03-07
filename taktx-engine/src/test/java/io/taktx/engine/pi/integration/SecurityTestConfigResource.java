/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.pi.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
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
 */
public class SecurityTestConfigResource implements QuarkusTestResourceLifecycleManager {

  /** RSA key-pair generated once per test-suite run, used for RS256 JWT signing. */
  static java.security.PublicKey rsaPublicKey;

  static java.security.PrivateKey rsaPrivateKey;

  /** Ed25519 key-pair generated once per test-suite run, used for Ed25519 message signing. */
  static java.security.PublicKey ed25519PublicKey;

  @Override
  public Map<String, String> start() {
    try {
      // ── RSA key-pair for command authorization ────────────────────────────
      KeyPair rsaKp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      rsaPublicKey = rsaKp.getPublic();
      rsaPrivateKey = rsaKp.getPrivate();
      String rsaPublicBase64 = Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded());

      // ── Ed25519 key-pair for engine-internal message signing ──────────────
      KeyPair ed25519Kp = SigningKeyGenerator.generate();
      ed25519PublicKey = ed25519Kp.getPublic();
      String ed25519PrivateBase64 = SigningKeyGenerator.encodePrivateKey(ed25519Kp.getPrivate());

      // EnvironmentVariableKeyProvider reads this system property — set it directly
      // so it is available before the engine bean is initialised.
      System.setProperty("taktx.signing.private-key", ed25519PrivateBase64);

      return Map.of(
          "taktx.test", "true",
          "kafka.devservices.auto-create-topics", "false",
          "taktx.security.authorization.enabled", "true",
          "taktx.security.authorization.nonce-check.enabled", "true",
          "taktx.platform.public-key", rsaPublicBase64,
          "taktx.security.signing.enabled", "true",
          // EnvironmentVariableKeyProvider reads this system property for any keyId
          "taktx.signing.private-key", ed25519PrivateBase64);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test keys", e);
    }
  }

  @Override
  public void stop() {
    // Clean up the system property so it does not bleed into the next Quarkus instance that
    // starts after this profile's app shuts down.
    System.clearProperty("taktx.signing.private-key");
  }
}
