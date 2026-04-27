/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.Constants;
import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

/**
 * Shared Ed25519 signing-key verification core.
 *
 * <p>Centralises the repeated pattern of: signature-header parsing → signing-key KTable lookup →
 * revoke check → trust-policy evaluation → role derivation. This eliminates the prior duplication
 * across {@link EngineAuthorizationService} (three nearly-identical inline blocks) and {@link
 * PublicKeyProvider} (duplicate KTable accessor), and provides a stable adoption seam for future
 * topic consumers (see Epic B2).
 *
 * <h2>Usage</h2>
 *
 * <ul>
 *   <li>For Ed25519 trust verification call {@link #verify(Header, KeyRole)}. The method throws
 *       {@link AuthorizationTokenException} on any failure, so callers only need to handle the
 *       happy path.
 *   <li>For raw KTable access (e.g. JWT key resolution in {@link PublicKeyProvider}) call {@link
 *       #resolveKey(String)} and apply the additional RSA / role checks yourself.
 * </ul>
 *
 * <h2>Adoption seam (Epic B2)</h2>
 *
 * <p>New topic consumers should call {@link #verify(Header, KeyRole)} and map the returned {@link
 * VerifiedMessageContext} to a {@link io.taktx.dto.CommandTrustMetadataDTO} or throw {@link
 * AuthorizationTokenException} as needed. The existing {@link EngineAuthorizationService} paths for
 * {@code process-instance}, {@code schedule-commands}, and {@code topic-meta-requested} already
 * delegate here; additional consumers (e.g. future durable-replay topic enforcement under Epic D4)
 * should follow the same pattern.
 *
 * <h2>Role derivation</h2>
 *
 * <p>Role is always derived from {@link SigningKeyDTO#effectiveRole()} on the key entry returned by
 * the {@code taktx-signing-keys} KTable — never from caller-supplied headers or payload fields.
 */
@ApplicationScoped
@Slf4j
public class VerificationCore {

  static final String SIG_HEADER = Constants.HEADER_ENGINE_SIGNATURE;

  private final TaktConfiguration config;
  private final KafkaStreams kafkaStreams;
  private final KeyTrustPolicy keyTrustPolicy;

  /** Lazy-initialised KTable store — null until first lookup. */
  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;

  @Inject
  public VerificationCore(
      TaktConfiguration config, KafkaStreams kafkaStreams, KeyTrustPolicy keyTrustPolicy) {
    this.config = config;
    this.kafkaStreams = kafkaStreams;
    this.keyTrustPolicy = keyTrustPolicy;
  }

  /** Test constructor — no CDI; uses {@link OpenKeyTrustPolicy}. */
  VerificationCore(TaktConfiguration config, KafkaStreams kafkaStreams) {
    this(config, kafkaStreams, new OpenKeyTrustPolicy());
  }

  /**
   * Resolves the signing-key KTable entry for the given key ID.
   *
   * <p>Returns {@code null} for unknown keys or when the store is temporarily unavailable. Does
   * <em>not</em> filter on revocation status — callers are responsible for that check.
   *
   * @param keyId the key ID to look up
   * @return the matching {@link SigningKeyDTO}, or {@code null} if absent or on store error
   */
  public SigningKeyDTO resolveKey(String keyId) {
    try {
      if (signingKeysStore == null) {
        signingKeysStore =
            kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                    config.getPrefixed(Stores.SIGNING_KEYS.getStorename()),
                    QueryableStoreTypes.keyValueStore()));
      }
      return signingKeysStore.get(keyId);
    } catch (Exception e) {
      log.warn("Could not read signing-keys store for keyId={}: {}", keyId, e.getMessage());
      return null;
    }
  }

  /**
   * Full Ed25519 trust verification: header parsing → key resolution → revoke check → trust-policy
   * evaluation → role derivation.
   *
   * <p>Only returns successfully when all checks pass. The cryptographic Ed25519 signature is
   * verified by the Kafka deserializer <em>before</em> this method is called; this method verifies
   * <em>trust</em>: is the key known, non-revoked, and trusted for the required role?
   *
   * @param sigHeader the {@code X-TaktX-Signature} header; {@code null} value causes a throw
   * @param requiredRole the minimum role the signer must hold
   * @return a {@link VerifiedMessageContext} describing the verified signer
   * @throws AuthorizationTokenException on any verification failure, with a descriptive reason
   */
  public VerifiedMessageContext verify(Header sigHeader, KeyRole requiredRole) {
    if (sigHeader == null || sigHeader.value() == null) {
      throw new AuthorizationTokenException(
          "Missing required " + SIG_HEADER + " header — required role: " + requiredRole);
    }

    String keyId = extractKeyId(sigHeader);
    SigningKeyDTO entry = resolveKey(keyId);

    if (entry == null) {
      throw new AuthorizationTokenException(
          "Unknown Ed25519 keyId '" + keyId + "' — signer not found in taktx-signing-keys KTable");
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException(
          "Revoked Ed25519 keyId '" + keyId + "' — rejecting message");
    }
    if (!keyTrustPolicy.isTrustedForRole(entry, requiredRole)) {
      throw new AuthorizationTokenException(
          "Signing keyId '"
              + keyId
              + "' (role="
              + entry.effectiveRole()
              + ") is not trusted for required role "
              + requiredRole);
    }

    return new VerifiedMessageContext(keyId, entry, entry.effectiveRole(), true);
  }

  /**
   * Extracts the key ID from the value of an {@code X-TaktX-Signature} header.
   *
   * <p>The header value format is {@code <keyId>.<base64signature>}; this method returns the
   * portion before the first {@code .}, or the entire value when no {@code .} is present.
   *
   * @param sigHeader a non-null header whose value is non-null
   * @return the extracted key ID
   */
  public static String extractKeyId(Header sigHeader) {
    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    return dot >= 0 ? headerValue.substring(0, dot) : headerValue;
  }
}
