/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnchoredKeyTrustPolicy}.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>null and revoked keys are always rejected regardless of signature
 *   <li>keys without a registration signature are rejected in anchored mode
 *   <li>keys with an invalid signature (tampered payload or wrong root key) are rejected
 *   <li>a validly countersigned ENGINE-role key is trusted for ENGINE operations
 *   <li>a validly countersigned CLIENT-role key is trusted for CLIENT operations only
 *   <li>a validly countersigned PLATFORM-role key satisfies ENGINE and CLIENT checks
 *   <li>role hierarchy is enforced after signature verification (ENGINE not trusted for PLATFORM)
 * </ul>
 */
class AnchoredKeyTrustPolicyTest {

  /** RSA 2048 key pair generated once per test run — represents the platform root key. */
  private static PublicKey rootPublicKey;

  private static PrivateKey rootPrivateKey;

  /** A second RSA key pair — used to produce invalid signatures for rejection tests. */
  private static PublicKey wrongPublicKey;

  private static PrivateKey wrongPrivateKey;

  private static AnchoredKeyTrustPolicy policy;

  @BeforeAll
  static void generateRootKeys() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);

    KeyPair rootKp = gen.generateKeyPair();
    rootPublicKey = rootKp.getPublic();
    rootPrivateKey = rootKp.getPrivate();

    KeyPair wrongKp = gen.generateKeyPair();
    wrongPublicKey = wrongKp.getPublic();
    wrongPrivateKey = wrongKp.getPrivate();

    policy = new AnchoredKeyTrustPolicy(rootPublicKey);
  }

  // ── Constructor guard ──────────────────────────────────────────────────────

  @Test
  void constructor_nullRootKey_throws() {
    assertThatThrownBy(() -> new AnchoredKeyTrustPolicy(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rootPublicKey");
  }

  // ── Null and revoked keys ──────────────────────────────────────────────────

  @Test
  void nullKey_alwaysRejected() {
    assertThat(policy.isTrustedForRole(null, KeyRole.CLIENT)).isFalse();
  }

  @Test
  void revokedKey_alwaysRejected_evenWithValidSignature() throws Exception {
    SigningKeyDTO key =
        countersigned("revoked-key", "pubkey-b64", "Ed25519", "owner", KeyRole.CLIENT);
    SigningKeyDTO revoked =
        SigningKeyDTO.builder()
            .keyId(key.getKeyId())
            .publicKeyBase64(key.getPublicKeyBase64())
            .algorithm(key.getAlgorithm())
            .owner(key.getOwner())
            .role(key.getRole())
            .status(KeyStatus.REVOKED) // ← override to REVOKED
            .registrationSignature(key.getRegistrationSignature())
            .build();
    assertThat(policy.isTrustedForRole(revoked, KeyRole.CLIENT)).isFalse();
  }

  // ── Missing registration signature ────────────────────────────────────────

  @Test
  void engineKey_noRegistrationSignature_rejected() {
    SigningKeyDTO key = unsigned("engine-key", "pubkey-b64", "Ed25519", "engine", KeyRole.ENGINE);
    assertThat(policy.isTrustedForRole(key, KeyRole.ENGINE)).isFalse();
  }

  @Test
  void clientKey_noRegistrationSignature_rejected() {
    // In anchored mode, CLIENT keys without a countersignature are also rejected.
    SigningKeyDTO key = unsigned("worker-key", "pubkey-b64", "Ed25519", "worker", KeyRole.CLIENT);
    assertThat(policy.isTrustedForRole(key, KeyRole.CLIENT)).isFalse();
  }

  @Test
  void engineKey_blankRegistrationSignature_rejected() {
    SigningKeyDTO key =
        SigningKeyDTO.builder()
            .keyId("engine-key")
            .publicKeyBase64("pubkey-b64")
            .algorithm("Ed25519")
            .owner("engine")
            .role(KeyRole.ENGINE)
            .status(KeyStatus.ACTIVE)
            .registrationSignature("   ") // blank
            .build();
    assertThat(policy.isTrustedForRole(key, KeyRole.ENGINE)).isFalse();
  }

  // ── Invalid / tampered signature ───────────────────────────────────────────

  @Test
  void engineKey_signedByWrongRootKey_rejected() throws Exception {
    // Sign with wrongPrivateKey — verification against rootPublicKey must fail.
    SigningKeyDTO key =
        countersignedWith(
            "engine-key", "pubkey-b64", "Ed25519", "engine", KeyRole.ENGINE, wrongPrivateKey);
    assertThat(policy.isTrustedForRole(key, KeyRole.ENGINE)).isFalse();
  }

  @Test
  void engineKey_tamperedPayload_rejected() throws Exception {
    // Sign the correct payload, then tamper with the publicKeyBase64.
    SigningKeyDTO original =
        countersigned("engine-key", "pubkey-b64", "Ed25519", "engine", KeyRole.ENGINE);
    SigningKeyDTO tampered =
        SigningKeyDTO.builder()
            .keyId(original.getKeyId())
            .publicKeyBase64("TAMPERED-pubkey") // different from signed payload
            .algorithm(original.getAlgorithm())
            .owner(original.getOwner())
            .role(original.getRole())
            .status(KeyStatus.ACTIVE)
            .registrationSignature(original.getRegistrationSignature())
            .build();
    assertThat(policy.isTrustedForRole(tampered, KeyRole.ENGINE)).isFalse();
  }

  @Test
  void garbageBase64_inSignatureField_rejected() {
    SigningKeyDTO key =
        SigningKeyDTO.builder()
            .keyId("engine-key")
            .publicKeyBase64("pubkey-b64")
            .algorithm("Ed25519")
            .owner("engine")
            .role(KeyRole.ENGINE)
            .status(KeyStatus.ACTIVE)
            .registrationSignature("not-valid-base64!!!")
            .build();
    assertThat(policy.isTrustedForRole(key, KeyRole.ENGINE)).isFalse();
  }

  // ── Valid countersignatures — role checks ──────────────────────────────────

  @Test
  void engineKey_validSignature_trustedForEngine() throws Exception {
    SigningKeyDTO key =
        countersigned("engine-key", "pubkey-b64", "Ed25519", "engine", KeyRole.ENGINE);
    assertThat(policy.isTrustedForRole(key, KeyRole.ENGINE)).isTrue();
  }

  @Test
  void engineKey_validSignature_trustedForClient() throws Exception {
    // ENGINE role satisfies CLIENT (role hierarchy: ENGINE ⊇ CLIENT)
    SigningKeyDTO key =
        countersigned("engine-key", "pubkey-b64", "Ed25519", "engine", KeyRole.ENGINE);
    assertThat(policy.isTrustedForRole(key, KeyRole.CLIENT)).isTrue();
  }

  @Test
  void engineKey_validSignature_notTrustedForPlatform() throws Exception {
    // ENGINE is below PLATFORM in the hierarchy
    SigningKeyDTO key =
        countersigned("engine-key", "pubkey-b64", "Ed25519", "engine", KeyRole.ENGINE);
    assertThat(policy.isTrustedForRole(key, KeyRole.PLATFORM)).isFalse();
  }

  @Test
  void clientKey_validSignature_trustedForClient() throws Exception {
    SigningKeyDTO key =
        countersigned("worker-key", "pubkey-b64", "Ed25519", "worker", KeyRole.CLIENT);
    assertThat(policy.isTrustedForRole(key, KeyRole.CLIENT)).isTrue();
  }

  @Test
  void clientKey_validSignature_notTrustedForEngine() throws Exception {
    // CLIENT cannot produce entry commands even with a valid countersignature
    SigningKeyDTO key =
        countersigned("worker-key", "pubkey-b64", "Ed25519", "worker", KeyRole.CLIENT);
    assertThat(policy.isTrustedForRole(key, KeyRole.ENGINE)).isFalse();
  }

  @Test
  void platformKey_validSignature_trustedForEngine() throws Exception {
    // PLATFORM ⊇ ENGINE ⊇ CLIENT
    SigningKeyDTO key =
        countersigned("platform-key", "pubkey-b64", "RSA", "platform", KeyRole.PLATFORM);
    assertThat(policy.isTrustedForRole(key, KeyRole.ENGINE)).isTrue();
  }

  @Test
  void platformKey_validSignature_trustedForPlatform() throws Exception {
    SigningKeyDTO key =
        countersigned("platform-key", "pubkey-b64", "RSA", "platform", KeyRole.PLATFORM);
    assertThat(policy.isTrustedForRole(key, KeyRole.PLATFORM)).isTrue();
  }

  // ── TRUSTED status (not ACTIVE, not REVOKED) ──────────────────────────────

  @Test
  void trustedStatusKey_validSignature_stillAccepted() throws Exception {
    // TRUSTED keys are accepted for verification (not REVOKED); AnchoredKeyTrustPolicy
    // delegates the status check to OpenKeyTrustPolicy which allows TRUSTED.
    SigningKeyDTO key =
        countersigned("retiring-key", "pubkey-b64", "Ed25519", "engine", KeyRole.ENGINE);
    SigningKeyDTO trustedStatus =
        SigningKeyDTO.builder()
            .keyId(key.getKeyId())
            .publicKeyBase64(key.getPublicKeyBase64())
            .algorithm(key.getAlgorithm())
            .owner(key.getOwner())
            .role(key.getRole())
            .status(KeyStatus.TRUSTED) // rotating out but still valid for verification
            .registrationSignature(key.getRegistrationSignature())
            .build();
    assertThat(policy.isTrustedForRole(trustedStatus, KeyRole.ENGINE)).isTrue();
  }

  // ── Canonical payload round-trip ──────────────────────────────────────────

  @Test
  void computeCanonicalPayload_matchesExpectedFormat() throws Exception {
    SigningKeyDTO key =
        countersigned("my-key-1", "abc123==", "Ed25519", "worker-billing", KeyRole.CLIENT);
    byte[] payload = SigningKeyRegistrar.computeCanonicalPayload(key);
    String payloadStr = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(payloadStr).isEqualTo("my-key-1|abc123==|Ed25519|worker-billing|CLIENT");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Builds a fully countersigned {@link SigningKeyDTO} using the test root private key.
   *
   * <p>This is the same operation that {@code scripts/generate_trust_anchor.sh} performs in
   * production: sign the pipe-delimited canonical payload with the platform RSA private key,
   * base64-encode the result, and store it as {@code registrationSignature}.
   */
  private SigningKeyDTO countersigned(
      String keyId, String publicKeyBase64, String algorithm, String owner, KeyRole role)
      throws Exception {
    return countersignedWith(keyId, publicKeyBase64, algorithm, owner, role, rootPrivateKey);
  }

  private SigningKeyDTO countersignedWith(
      String keyId,
      String publicKeyBase64,
      String algorithm,
      String owner,
      KeyRole role,
      PrivateKey signingKey)
      throws Exception {
    // Build the key first (without signature) to derive the canonical payload
    SigningKeyDTO unsignedKey =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64(publicKeyBase64)
            .algorithm(algorithm)
            .owner(owner)
            .role(role)
            .status(KeyStatus.ACTIVE)
            .build();

    // Produce the registration signature: SHA256withRSA over the canonical payload
    byte[] canonical = SigningKeyRegistrar.computeCanonicalPayload(unsignedKey);
    Signature sig = Signature.getInstance("SHA256withRSA");
    sig.initSign(signingKey);
    sig.update(canonical);
    String registrationSignature = Base64.getEncoder().encodeToString(sig.sign());

    return SigningKeyDTO.builder()
        .keyId(keyId)
        .publicKeyBase64(publicKeyBase64)
        .algorithm(algorithm)
        .owner(owner)
        .role(role)
        .status(KeyStatus.ACTIVE)
        .registrationSignature(registrationSignature)
        .build();
  }

  private static SigningKeyDTO unsigned(
      String keyId, String publicKeyBase64, String algorithm, String owner, KeyRole role) {
    return SigningKeyDTO.builder()
        .keyId(keyId)
        .publicKeyBase64(publicKeyBase64)
        .algorithm(algorithm)
        .owner(owner)
        .role(role)
        .status(KeyStatus.ACTIVE)
        .build();
  }
}
