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
import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.engine.generic.ClockProducer;
import io.taktx.engine.generic.MutableClock;
import io.taktx.engine.license.LicenseManager;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.security.MessageSigningService;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningKeyRegistrar;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Quarkus test resource that provisions anchored-mode security material for the security
 * integration profile.
 *
 * <p>The generated/observed public keys are exposed via static fields so tests can produce valid
 * JWTs and verify Ed25519 signatures without any shared mutable state. The engine itself is also
 * started with a stable pre-generated Ed25519 identity and a valid engine-key countersignature so
 * startup-static anchored signing is active for the whole suite.
 *
 * <p>The RSA public key is injected via {@code taktx.platform.public-key} so the engine runs in
 * anchored mode, and it is also published to the {@code taktx-signing-keys} KTable under {@link
 * #PLATFORM_KID} in {@code @BeforeAll}, matching the {@code kid} header the JWT builder sets. That
 * keeps JWT issuer-key resolution on the same KTable path the real platform uses.
 */
public class SecurityTestConfigResource implements QuarkusTestResourceLifecycleManager {

  /** Key ID used as JWT {@code kid} header and as the KTable key for the RSA public key. */
  static final String PLATFORM_KID = "platform-test-key";

  /** RSA key-pair generated once per test-suite run, used for RS256 JWT signing. */
  static java.security.PublicKey rsaPublicKey;

  static java.security.PrivateKey rsaPrivateKey;

  /** Base64-encoded RSA public key — exposed so tests can publish it to the signing-keys topic. */
  static String rsaPublicKeyBase64;

  /** Base64-encoded engine public key — exposed for the test client's deserializer. */
  static String enginePublicKeyBase64;

  /** Active engine signing key ID, exposed for assertions. */
  static String engineKeyId;

  private static String enginePrivateKeyBase64;
  private static String engineRegistrationSignature;

  private static final String PLATFORM_PRIVATE_KEY_SYS_PROP = "taktx.test.platform.private-key";

  @Override
  public Map<String, String> start() {
    // Close the shared singleton engine (used by all default-profile tests) BEFORE Quarkus
    // restarts for the security profile.  Its Kafka consumers would otherwise keep trying to
    // reconnect to the now-unavailable (restarted) broker and flood the logs throughout the
    // entire security test run and all subsequent tests.  After this call, instance == null so
    // the first default-profile test after the security suite creates a fresh instance bound to
    // the new broker address.
    SingletonBpmnTestEngine.closeIfRunning();
    resetFixedTestClock();

    try {
      // ── RSA key-pair for command authorization ────────────────────────────
      java.security.KeyPair rsaKp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      rsaPublicKey = rsaKp.getPublic();
      rsaPrivateKey = rsaKp.getPrivate();
      rsaPublicKeyBase64 = Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded());

      java.security.KeyPair engineKp = SigningKeyGenerator.generate();
      enginePrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(engineKp.getPrivate());
      enginePublicKeyBase64 = SigningKeyGenerator.encodePublicKey(engineKp.getPublic());
      engineKeyId = "security-test-engine-" + UUID.randomUUID();
      engineRegistrationSignature =
          registrationSignature(engineKeyId, enginePublicKeyBase64, "Ed25519", KeyRole.ENGINE);

      System.setProperty("taktx.signing.identity-source", "env");
      System.setProperty("taktx.signing.key-id", engineKeyId);
      System.setProperty("taktx.signing.private-key", enginePrivateKeyBase64);
      System.setProperty("taktx.signing.public-key", enginePublicKeyBase64);
      System.setProperty("taktx.platform.public-key", rsaPublicKeyBase64);
      System.setProperty("taktx.engine.key-registration-signature", engineRegistrationSignature);
      System.setProperty(
          PLATFORM_PRIVATE_KEY_SYS_PROP,
          Base64.getEncoder().encodeToString(rsaPrivateKey.getEncoded()));

      return Map.of(
          "taktx.test",
          "true",
          "kafka.devservices.auto-create-topics",
          "false",
          "taktx.signing.identity-source",
          "env",
          "taktx.signing.key-id",
          engineKeyId,
          "taktx.signing.private-key",
          enginePrivateKeyBase64,
          "taktx.signing.public-key",
          enginePublicKeyBase64,
          "taktx.platform.public-key",
          rsaPublicKeyBase64,
          "taktx.engine.key-registration-signature",
          engineRegistrationSignature);
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
        licenseManager.updateFromLicensePush("TEST", Integer.MAX_VALUE);
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
    System.clearProperty("taktx.signing.identity-source");
    System.clearProperty("taktx.signing.key-id");
    System.clearProperty("taktx.signing.private-key");
    System.clearProperty("taktx.signing.public-key");
    System.clearProperty("taktx.platform.public-key");
    System.clearProperty("taktx.engine.key-registration-signature");
    System.clearProperty(PLATFORM_PRIVATE_KEY_SYS_PROP);
    enginePrivateKeyBase64 = null;
    engineRegistrationSignature = null;
    enginePublicKeyBase64 = null;
    engineKeyId = null;
  }

  static String registrationSignature(
      String keyId, String publicKeyBase64, String algorithm, KeyRole role) {
    try {
      SigningKeyDTO dto =
          SigningKeyDTO.builder()
              .keyId(keyId)
              .publicKeyBase64(publicKeyBase64)
              .algorithm(algorithm)
              .role(role)
              .build();
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(rsaPrivateKey);
      signature.update(SigningKeyRegistrar.computeCanonicalPayload(dto));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to countersign security test key", e);
    }
  }

  private static void resetFixedTestClock() {
    if (ClockProducer.FIXED_CLOCK instanceof MutableClock fixedClock) {
      fixedClock.set(Instant.parse(ClockProducer.INITIAL_TIME));
    }
  }
}
