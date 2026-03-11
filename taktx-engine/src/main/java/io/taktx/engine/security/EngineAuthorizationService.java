/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.quarkus.runtime.Startup;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.Constants;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TokenClaims;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.AuthorizationTokenValidator;
import io.taktx.security.EngineSigningKeysHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

/**
 * Validates incoming Kafka commands and worker responses.
 *
 * <p>Two validation paths:
 *
 * <ul>
 *   <li>{@code X-TaktX-Authorization} (RS256 JWT) — used by Console/Platform for start-process and
 *       abort commands; validates claims, expiry, and replay via {@link NonceStore}.
 *   <li>{@code X-TaktX-Signature} (Ed25519) — used by worker processes for task responses and by
 *       the engine itself for internal sub-process/call-activity triggers. Worker keys are looked
 *       up in the {@code taktx-signing-keys} KTable; {@code REVOKED} or unknown keys are rejected
 *       here. Engine-internal keys (matching {@code taktx.signing.key-id}) are trusted as-is.
 * </ul>
 *
 * <p>When authorization is disabled ({@code taktx.security.authorization.enabled=false}), returns
 * {@code null} without validating anything.
 */
@ApplicationScoped
@Startup
@Slf4j
public class EngineAuthorizationService {

  static final String AUTH_HEADER = Constants.HEADER_AUTHORIZATION;
  static final String SIG_HEADER = Constants.HEADER_ENGINE_SIGNATURE;

  private final TaktConfiguration config;
  private final NonceStore nonceStore;
  private final AuthorizationTokenValidator validator;
  private final KafkaStreams kafkaStreams;

  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;

  @Inject
  public EngineAuthorizationService(
      TaktConfiguration config,
      PublicKeyProvider publicKeyProvider,
      NonceStore nonceStore,
      KafkaStreams kafkaStreams) {
    this.config = config;
    this.nonceStore = nonceStore;
    this.kafkaStreams = kafkaStreams;
    this.validator = new AuthorizationTokenValidator(publicKeyProvider);
  }

  @PostConstruct
  void registerKeyResolver() {
    EngineSigningKeysHolder.set(this::resolvePublicKeyFromKTable);
    log.debug(
        "EngineAuthorizationService registered Ed25519 key resolver in EngineSigningKeysHolder");
  }

  @PreDestroy
  void clearKeyResolver() {
    EngineSigningKeysHolder.clear();
  }

  /**
   * Resolves the base64-encoded Ed25519 public key for the given keyId from the {@code
   * taktx-signing-keys} KTable. Returns {@code null} for unknown or REVOKED keys. Used by {@link
   * io.taktx.serdes.JsonDeserializer} via {@link EngineSigningKeysHolder}.
   */
  private String resolvePublicKeyFromKTable(String keyId) {
    SigningKeyDTO entry = lookupSigningKey(keyId);
    if (entry == null || entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) return null;
    return entry.getPublicKeyBase64();
  }

  /**
   * Authorises an incoming command on {@code process-instance-trigger}.
   *
   * <p>Decision tree:
   *
   * <ol>
   *   <li>{@code X-TaktX-Authorization} (RS256 JWT) — external command from console/ingester;
   *       validated here with full claim and nonce checks.
   *   <li>{@code X-TaktX-Signature} (Ed25519) — worker response or engine-internal command;
   *       engine-internal keys are trusted as-is, worker keys are enforced against the {@code
   *       taktx-signing-keys} KTable (REVOKED/unknown → rejected).
   *   <li>No header — rejected when authorization is enabled.
   * </ol>
   *
   * @return the {@code auditId} from a JWT token, or {@code null} for Ed25519-signed records
   * @throws AuthorizationTokenException if validation fails — record must be dropped
   */
  public String authorize(Headers headers, ProcessInstanceTriggerDTO trigger) {
    if (!config.isAuthorizationEnabled()) {
      return null;
    }

    Header authHeader = headers.lastHeader(AUTH_HEADER);
    if (authHeader != null && authHeader.value() != null) {
      return authorizeViaJwt(authHeader, trigger);
    }

    Header sigHeader = headers.lastHeader(SIG_HEADER);
    if (sigHeader != null && sigHeader.value() != null) {
      authorizeViaEd25519(sigHeader, trigger);
      return null;
    }

    throw new AuthorizationTokenException(
        "Missing required "
            + AUTH_HEADER
            + " or "
            + SIG_HEADER
            + " header on command "
            + trigger.getClass().getSimpleName());
  }

  // ── JWT path ────────────────────────────────────────────────────────────────

  private String authorizeViaJwt(Header authHeader, ProcessInstanceTriggerDTO trigger) {
    String rawJwt = new String(authHeader.value(), StandardCharsets.UTF_8);
    TokenClaims claims = validator.validate(rawJwt);
    validateClaimsMatchCommand(claims, trigger);
    if (config.isNonceCheckEnabled() && !nonceStore.checkAndRecord(claims.getAuditId())) {
      throw new AuthorizationTokenException("Replayed auditId detected: " + claims.getAuditId());
    }
    log.info(
        "✅ Authorised (JWT) command={} user={} auditId={}",
        trigger.getClass().getSimpleName(),
        claims.getUserId(),
        claims.getAuditId());
    return claims.getAuditId();
  }

  // ── Ed25519 path ──────────────────────────────────────────────────────────────

  /**
   * Enforces Ed25519 authorization for worker responses and engine-internal commands.
   *
   * <ul>
   *   <li>Engine-internal key (matches {@code taktx.signing.key-id}) — trusted without a KTable
   *       lookup; the deserializer has already verified the signature cryptographically.
   *   <li>Worker key — must be present in the {@code taktx-signing-keys} KTable with status {@code
   *       TRUSTED}. {@code REVOKED} or unknown keys cause an {@link AuthorizationTokenException}
   *       which drops the record.
   * </ul>
   */
  private void authorizeViaEd25519(Header sigHeader, ProcessInstanceTriggerDTO trigger) {
    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    String keyId = dot >= 0 ? headerValue.substring(0, dot) : headerValue;

    boolean isEngineKey = config.getSigningKeyId().map(kid -> kid.equals(keyId)).orElse(false);
    if (isEngineKey) {
      log.trace(
          "✅ Authorised (engine-internal Ed25519) command={} keyId={}",
          trigger.getClass().getSimpleName(),
          keyId);
      return;
    }

    // Worker key — look up and enforce status
    SigningKeyDTO entry = lookupSigningKey(keyId);
    if (entry == null) {
      throw new AuthorizationTokenException(
          "Unknown Ed25519 keyId '"
              + keyId
              + "' — rejecting command "
              + trigger.getClass().getSimpleName());
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException(
          "Revoked Ed25519 keyId '"
              + keyId
              + "' — rejecting command "
              + trigger.getClass().getSimpleName());
    }
    log.info(
        "✅ Authorised (worker Ed25519) command={} keyId={} owner={}",
        trigger.getClass().getSimpleName(),
        keyId,
        entry.getOwner());
  }

  private SigningKeyDTO lookupSigningKey(String keyId) {
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

  private void validateClaimsMatchCommand(TokenClaims claims, ProcessInstanceTriggerDTO trigger) {
    if (trigger instanceof StartCommandDTO start) {
      if (!"START".equals(claims.getAction())) {
        throw new AuthorizationTokenException(
            "Token action '" + claims.getAction() + "' does not match START command");
      }
      String defId =
          start.getProcessDefinitionKey() != null
              ? start.getProcessDefinitionKey().getProcessDefinitionId()
              : null;
      Integer defVersion =
          start.getProcessDefinitionKey() != null
              ? start.getProcessDefinitionKey().getVersion()
              : null;
      if (claims.getProcessDefinitionId() != null
          && !claims.getProcessDefinitionId().equals(defId)) {
        throw new AuthorizationTokenException(
            "Token processDefinitionId '"
                + claims.getProcessDefinitionId()
                + "' does not match command '"
                + defId
                + "'");
      }
      if (claims.getVersion() > 0 && defVersion != null && claims.getVersion() != defVersion) {
        throw new AuthorizationTokenException(
            "Token version "
                + claims.getVersion()
                + " does not match command version "
                + defVersion);
      }
    } else if (trigger instanceof AbortTriggerDTO) {
      if (!"CANCEL".equals(claims.getAction())) {
        throw new AuthorizationTokenException(
            "Token action '" + claims.getAction() + "' does not match CANCEL command");
      }
    } else {
      log.debug("No claim matching defined for {}, allowing", trigger.getClass().getSimpleName());
    }
  }
}
