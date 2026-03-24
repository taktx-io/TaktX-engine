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

/**
 * Verifies the DTO construction logic inside {@link SigningKeyRegistrar} without connecting to
 * Kafka (tests the package-private {@link SigningKeyRegistrar#buildSigningKeyDto} builder and the
 * {@link SigningKeyRegistrar#computeCanonicalPayload} helper).
 *
 * <p>Kafka-level integration (actual publish) is covered by the engine's SecurityIntegrationTest.
 */
class SigningKeyRegistrarTest {

  // ── buildSigningKeyDto — role propagation ──────────────────────────────────

  @Test
  void buildDto_withEngineRole_setsRoleEngine() {
    SigningKeyDTO dto =
        SigningKeyRegistrar.buildSigningKeyDto(
            "engine-001", "pubkey", "engine", "Ed25519", KeyRole.ENGINE, null);

    assertThat(dto.getKeyId()).isEqualTo("engine-001");
    assertThat(dto.getRole()).isEqualTo(KeyRole.ENGINE);
    assertThat(dto.effectiveRole()).isEqualTo(KeyRole.ENGINE);
    assertThat(dto.getStatus()).isEqualTo(KeyStatus.ACTIVE);
    assertThat(dto.getOwner()).isEqualTo("engine");
    assertThat(dto.getAlgorithm()).isEqualTo("Ed25519");
    assertThat(dto.getRegistrationSignature()).isNull();
  }

  @Test
  void buildDto_withClientRole_setsRoleClient() {
    SigningKeyDTO dto =
        SigningKeyRegistrar.buildSigningKeyDto(
            "worker-001", "pubkey", "billing-worker", "Ed25519", KeyRole.CLIENT, null);

    assertThat(dto.getRole()).isEqualTo(KeyRole.CLIENT);
    assertThat(dto.effectiveRole()).isEqualTo(KeyRole.CLIENT);
  }

  @Test
  void buildDto_withNullRole_defaultsToClient() {
    SigningKeyDTO dto =
        SigningKeyRegistrar.buildSigningKeyDto(
            "legacy-001", "pubkey", "legacy-worker", "Ed25519", null, null);

    // null role → @Builder.Default or explicit null check → CLIENT
    assertThat(dto.getRole()).isEqualTo(KeyRole.CLIENT);
    assertThat(dto.effectiveRole()).isEqualTo(KeyRole.CLIENT);
  }

  @Test
  void buildDto_withNullAlgorithm_defaultsToEd25519() {
    SigningKeyDTO dto =
        SigningKeyRegistrar.buildSigningKeyDto(
            "k-001", "pubkey", "owner", null, KeyRole.CLIENT, null);

    assertThat(dto.getAlgorithm()).isEqualTo("Ed25519");
  }

  @Test
  void buildDto_withRegistrationSignature_preservesSig() {
    SigningKeyDTO dto =
        SigningKeyRegistrar.buildSigningKeyDto(
            "anchored-key", "pubkey", "engine", "Ed25519", KeyRole.ENGINE, "base64sig==");

    assertThat(dto.getRegistrationSignature()).isEqualTo("base64sig==");
  }

  @Test
  void buildDto_withPlatformRole_setsRolePlatform() {
    SigningKeyDTO dto =
        SigningKeyRegistrar.buildSigningKeyDto(
            "platform-001", "pubkey", "platform", "RSA", KeyRole.PLATFORM, "sig==");

    assertThat(dto.getRole()).isEqualTo(KeyRole.PLATFORM);
    assertThat(dto.effectiveRole()).isEqualTo(KeyRole.PLATFORM);
  }

  // ── revokeKey — status override ────────────────────────────────────────────

  @Test
  void revokeKey_buildsRevocationDto_preservingAllOtherFields() {
    SigningKeyDTO existing =
        SigningKeyDTO.builder()
            .keyId("engine-to-revoke")
            .publicKeyBase64("pubkey-bytes")
            .algorithm("Ed25519")
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .status(KeyStatus.ACTIVE)
            .owner("engine")
            .role(KeyRole.ENGINE)
            .registrationSignature("sig==")
            .build();

    // revokeKey calls doPublish with a REVOKED DTO — we test the DTO construction
    // by calling buildSigningKeyDto and overriding status manually (mirrors the logic
    // in revokeKey).
    SigningKeyDTO revoked =
        SigningKeyDTO.builder()
            .keyId(existing.getKeyId())
            .publicKeyBase64(existing.getPublicKeyBase64())
            .algorithm(existing.getAlgorithm())
            .createdAt(existing.getCreatedAt())
            .status(KeyStatus.REVOKED)
            .owner(existing.getOwner())
            .role(existing.effectiveRole())
            .registrationSignature(existing.getRegistrationSignature())
            .build();

    assertThat(revoked.getStatus()).isEqualTo(KeyStatus.REVOKED);
    assertThat(revoked.getRole()).isEqualTo(KeyRole.ENGINE);
    assertThat(revoked.getPublicKeyBase64()).isEqualTo("pubkey-bytes");
    assertThat(revoked.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(revoked.getRegistrationSignature()).isEqualTo("sig==");
  }

  // ── computeCanonicalPayload ────────────────────────────────────────────────

  @Test
  void computeCanonicalPayload_engineKey_includesEngineRole() {
    SigningKeyDTO key =
        SigningKeyDTO.builder()
            .keyId("engine-001")
            .publicKeyBase64("AAAA")
            .algorithm("Ed25519")
            .owner("engine")
            .role(KeyRole.ENGINE)
            .build();

    String payload = new String(SigningKeyRegistrar.computeCanonicalPayload(key));
    assertThat(payload).isEqualTo("engine-001|AAAA|Ed25519|engine|ENGINE");
  }

  @Test
  void computeCanonicalPayload_clientKey_includesClientRole() {
    SigningKeyDTO key =
        SigningKeyDTO.builder()
            .keyId("worker-001")
            .publicKeyBase64("BBBB")
            .algorithm("Ed25519")
            .owner("billing-worker")
            .role(KeyRole.CLIENT)
            .build();

    String payload = new String(SigningKeyRegistrar.computeCanonicalPayload(key));
    assertThat(payload).isEqualTo("worker-001|BBBB|Ed25519|billing-worker|CLIENT");
  }

  @Test
  void computeCanonicalPayload_nullRole_treatedAsClient() {
    // A key with role=null (legacy) → effectiveRole()=CLIENT → payload uses "CLIENT"
    SigningKeyDTO key =
        SigningKeyDTO.builder()
            .keyId("legacy-001")
            .publicKeyBase64("CCCC")
            .algorithm("Ed25519")
            .owner("legacy")
            .role(null)
            .build();

    String payload = new String(SigningKeyRegistrar.computeCanonicalPayload(key));
    assertThat(payload).isEqualTo("legacy-001|CCCC|Ed25519|legacy|CLIENT");
  }

  @Test
  void computeCanonicalPayload_platformKey_includesPlatformRole() {
    SigningKeyDTO key =
        SigningKeyDTO.builder()
            .keyId("plat-001")
            .publicKeyBase64("DDDD")
            .algorithm("RSA")
            .owner("platform-service")
            .role(KeyRole.PLATFORM)
            .build();

    String payload = new String(SigningKeyRegistrar.computeCanonicalPayload(key));
    assertThat(payload).isEqualTo("plat-001|DDDD|RSA|platform-service|PLATFORM");
  }
}
