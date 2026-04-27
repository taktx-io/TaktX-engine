/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
import io.taktx.security.PublicKeySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;

/**
 * Resolves the Platform Service RSA public key from the {@code taktx-signing-keys} KTable.
 *
 * <p>The platform publishes its RSA public key to the compacted {@code taktx-signing-keys} topic
 * under a custom keyId and sets that same keyId as the {@code kid} header in every JWT it issues.
 * {@link #getKey(String)} looks up the KTable by the {@code kid} extracted from the JWT header, so
 * key rotation is handled automatically without a restart.
 *
 * <p>KTable access is delegated to {@link VerificationCore#resolveKey(String)} to eliminate the
 * prior duplicate store accessor. The RSA-specific trust, revoke, and algorithm checks remain here.
 *
 * <p>If the KTable does not contain a non-revoked entry for the requested keyId, {@link
 * #getKey(String)} throws {@link AuthorizationTokenException} and the JWT is rejected.
 *
 * <p>Implements {@link PublicKeySource} so it can be passed directly to {@link
 * io.taktx.security.AuthorizationTokenValidator}.
 */
@ApplicationScoped
@Slf4j
public class PublicKeyProvider implements PublicKeySource {

  private static final String RSA_ALGORITHM = "RSA";

  private final VerificationCore verificationCore;
  private final KeyTrustPolicy keyTrustPolicy;

  @Inject
  public PublicKeyProvider(VerificationCore verificationCore, KeyTrustPolicy keyTrustPolicy) {
    this.verificationCore = verificationCore;
    this.keyTrustPolicy = keyTrustPolicy;
  }

  /** Test constructor — no CDI; mirrors the previous (config, kafkaStreams, keyTrustPolicy) API. */
  PublicKeyProvider(
      TaktConfiguration config, KafkaStreams kafkaStreams, KeyTrustPolicy keyTrustPolicy) {
    this(new VerificationCore(config, kafkaStreams, keyTrustPolicy), keyTrustPolicy);
  }

  /** Test constructor — no CDI; uses {@link OpenKeyTrustPolicy}. */
  PublicKeyProvider(TaktConfiguration config, KafkaStreams kafkaStreams) {
    this(new VerificationCore(config, kafkaStreams), new OpenKeyTrustPolicy());
  }

  /**
   * Returns the RSA public key stored under {@code kid} in the {@code taktx-signing-keys} KTable.
   *
   * @param kid the key ID extracted from the JWT {@code kid} header
   * @return the resolved {@link PublicKey}
   * @throws AuthorizationTokenException if the key is missing, revoked, untrusted, or cannot be
   *     parsed
   */
  @Override
  public PublicKey getKey(String kid) {
    if (kid == null || kid.isBlank()) {
      throw new AuthorizationTokenException("JWT 'kid' header is missing or blank");
    }
    SigningKeyDTO entry = verificationCore.resolveKey(kid);
    if (entry == null) {
      throw new AuthorizationTokenException("No signing key found in KTable for kid='" + kid + "'");
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException("Signing key kid='" + kid + "' has been revoked");
    }
    if (!keyTrustPolicy.isTrustedForRole(entry, KeyRole.PLATFORM)) {
      throw new AuthorizationTokenException(
          "Signing key kid='" + kid + "' is not trusted as a PLATFORM JWT issuer key");
    }
    if (!RSA_ALGORITHM.equalsIgnoreCase(entry.getAlgorithm())) {
      throw new AuthorizationTokenException(
          "Signing key kid='"
              + kid
              + "' is not an RSA JWT issuer key (algorithm="
              + entry.getAlgorithm()
              + ")");
    }
    try {
      return parseRsaPublicKey(entry.getPublicKeyBase64());
    } catch (Exception e) {
      throw new AuthorizationTokenException(
          "Failed to parse RSA public key for kid='" + kid + "': " + e.getMessage(), e);
    }
  }

  // ── private ──────────────────────────────────────────────────────────────────

  private static PublicKey parseRsaPublicKey(String base64) throws GeneralSecurityException {
    byte[] keyBytes = Base64.getDecoder().decode(base64);
    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
  }
}
