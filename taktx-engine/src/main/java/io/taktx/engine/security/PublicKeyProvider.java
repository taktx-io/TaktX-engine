/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.SigningKeyDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.PublicKeySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

/**
 * Resolves the Platform Service RSA public key from the {@code taktx-signing-keys} KTable.
 *
 * <p>The platform publishes its RSA public key to the compacted {@code taktx-signing-keys} topic
 * under a custom keyId and sets that same keyId as the {@code kid} header in every JWT it issues.
 * {@link #getKey(String)} looks up the KTable by the {@code kid} extracted from the JWT header, so
 * key rotation is handled automatically without a restart.
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

  private final TaktConfiguration config;
  private final KafkaStreams kafkaStreams;

  /** Lazy-initialised KTable store — null until first lookup. */
  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;

  @Inject
  public PublicKeyProvider(TaktConfiguration config, KafkaStreams kafkaStreams) {
    this.config = config;
    this.kafkaStreams = kafkaStreams;
  }

  /**
   * Returns the RSA public key stored under {@code kid} in the {@code taktx-signing-keys} KTable.
   *
   * @param kid the key ID extracted from the JWT {@code kid} header
   * @return the resolved {@link PublicKey}
   * @throws AuthorizationTokenException if the key is missing, revoked, or cannot be parsed
   */
  @Override
  public PublicKey getKey(String kid) {
    if (kid == null || kid.isBlank()) {
      throw new AuthorizationTokenException("JWT 'kid' header is missing or blank");
    }
    SigningKeyDTO entry = lookupKeyEntry(kid);
    if (entry == null) {
      throw new AuthorizationTokenException("No signing key found in KTable for kid='" + kid + "'");
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException("Signing key kid='" + kid + "' has been revoked");
    }
    try {
      return parseRsaPublicKey(entry.getPublicKeyBase64());
    } catch (Exception e) {
      throw new AuthorizationTokenException(
          "Failed to parse RSA public key for kid='" + kid + "': " + e.getMessage(), e);
    }
  }

  // ── private ──────────────────────────────────────────────────────────────────

  private SigningKeyDTO lookupKeyEntry(String kid) {
    try {
      if (signingKeysStore == null) {
        signingKeysStore =
            kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                    config.getPrefixed(Stores.SIGNING_KEYS.getStorename()),
                    QueryableStoreTypes.keyValueStore()));
      }
      return signingKeysStore.get(kid);
    } catch (Exception e) {
      log.debug("Could not read signing-keys store for kid={}: {}", kid, e.getMessage());
      return null;
    }
  }

  private static PublicKey parseRsaPublicKey(String base64) throws Exception {
    byte[] keyBytes = Base64.getDecoder().decode(base64);
    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
  }
}
