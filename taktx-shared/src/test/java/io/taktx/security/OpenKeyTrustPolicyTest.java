/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OpenKeyTrustPolicyTest {

  private final OpenKeyTrustPolicy policy = new OpenKeyTrustPolicy();

  // ── ENGINE role ────────────────────────────────────────────────────────────

  @Test
  void engineKey_trustedForEngine() {
    assertThat(policy.isTrustedForRole(key(KeyRole.ENGINE, KeyStatus.ACTIVE), KeyRole.ENGINE))
        .isTrue();
  }

  @Test
  void engineKey_trustedForClient() {
    assertThat(policy.isTrustedForRole(key(KeyRole.ENGINE, KeyStatus.ACTIVE), KeyRole.CLIENT))
        .isTrue();
  }

  @Test
  void engineKey_notTrustedForPlatform() {
    assertThat(policy.isTrustedForRole(key(KeyRole.ENGINE, KeyStatus.ACTIVE), KeyRole.PLATFORM))
        .isFalse();
  }

  // ── CLIENT role ────────────────────────────────────────────────────────────

  @Test
  void clientKey_trustedForClient() {
    assertThat(policy.isTrustedForRole(key(KeyRole.CLIENT, KeyStatus.ACTIVE), KeyRole.CLIENT))
        .isTrue();
  }

  @Test
  void clientKey_notTrustedForEngine() {
    assertThat(policy.isTrustedForRole(key(KeyRole.CLIENT, KeyStatus.ACTIVE), KeyRole.ENGINE))
        .isFalse();
  }

  @Test
  void clientKey_notTrustedForPlatform() {
    assertThat(policy.isTrustedForRole(key(KeyRole.CLIENT, KeyStatus.ACTIVE), KeyRole.PLATFORM))
        .isFalse();
  }

  // ── PLATFORM role ──────────────────────────────────────────────────────────

  @Test
  void platformKey_trustedForEngine() {
    assertThat(policy.isTrustedForRole(key(KeyRole.PLATFORM, KeyStatus.ACTIVE), KeyRole.ENGINE))
        .isTrue();
  }

  @Test
  void platformKey_trustedForClient() {
    assertThat(policy.isTrustedForRole(key(KeyRole.PLATFORM, KeyStatus.ACTIVE), KeyRole.CLIENT))
        .isTrue();
  }

  @Test
  void platformKey_trustedForPlatform() {
    assertThat(policy.isTrustedForRole(key(KeyRole.PLATFORM, KeyStatus.ACTIVE), KeyRole.PLATFORM))
        .isTrue();
  }

  // ── Revoked keys ──────────────────────────────────────────────────────────

  @Test
  void revokedEngineKey_neverTrusted() {
    assertThat(policy.isTrustedForRole(key(KeyRole.ENGINE, KeyStatus.REVOKED), KeyRole.CLIENT))
        .isFalse();
  }

  @Test
  void revokedClientKey_neverTrusted() {
    assertThat(policy.isTrustedForRole(key(KeyRole.CLIENT, KeyStatus.REVOKED), KeyRole.CLIENT))
        .isFalse();
  }

  // ── Null handling ──────────────────────────────────────────────────────────

  @Test
  void nullKey_neverTrusted() {
    assertThat(policy.isTrustedForRole(null, KeyRole.CLIENT)).isFalse();
  }

  @Test
  void nullRole_treatedAsClient_trustedForClient() {
    // A key with role=null has effectiveRole()=CLIENT
    SigningKeyDTO nullRoleKey =
        new SigningKeyDTO(
            "key-1", "pubkey", "Ed25519", Instant.now(), KeyStatus.ACTIVE, "owner", null);
    assertThat(policy.isTrustedForRole(nullRoleKey, KeyRole.CLIENT)).isTrue();
  }

  @Test
  void nullRole_treatedAsClient_notTrustedForEngine() {
    SigningKeyDTO nullRoleKey =
        new SigningKeyDTO(
            "key-1", "pubkey", "Ed25519", Instant.now(), KeyStatus.ACTIVE, "owner", null);
    assertThat(policy.isTrustedForRole(nullRoleKey, KeyRole.ENGINE)).isFalse();
  }

  // ── TRUSTED status ─────────────────────────────────────────────────────────

  @Test
  void trustedStatusKey_stillAcceptedForVerification() {
    // TRUSTED keys are not REVOKED — they should still pass role checks
    assertThat(policy.isTrustedForRole(key(KeyRole.ENGINE, KeyStatus.TRUSTED), KeyRole.ENGINE))
        .isTrue();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private static SigningKeyDTO key(KeyRole role, KeyStatus status) {
    return SigningKeyDTO.builder()
        .keyId("test-key")
        .publicKeyBase64("dummy")
        .algorithm("Ed25519")
        .status(status)
        .owner("test")
        .role(role)
        .build();
  }
}
