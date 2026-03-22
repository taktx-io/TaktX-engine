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
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.Constants;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TokenClaims;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.license.LicenseManager;
import io.taktx.engine.license.LicenseState;
import io.taktx.engine.pd.Stores;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.AuthorizationTokenValidator;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
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
 *       the engine itself for internal sub-process/call-activity triggers. All keys are looked up
 *       in the {@code taktx-signing-keys} KTable; {@code REVOKED} or unknown keys are rejected
 *       here.
 * </ul>
 *
 * <p>When authorization is disabled in the latest {@link GlobalConfigurationDTO}, returns {@code
 * null} without validating anything.
 */
@ApplicationScoped
@Startup
@Slf4j
public class EngineAuthorizationService {

  static final String AUTH_HEADER = Constants.HEADER_AUTHORIZATION;
  static final String SIG_HEADER = Constants.HEADER_ENGINE_SIGNATURE;

  private final TaktConfiguration config;
  private final GlobalConfigStore globalConfigStore;
  private final NonceStore nonceStore;
  private final AuthorizationTokenValidator validator;
  private final KafkaStreams kafkaStreams;
  private final LicenseManager licenseManager;
  private final KeyTrustPolicy keyTrustPolicy;

  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;

  @Inject
  public EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      PublicKeyProvider publicKeyProvider,
      NonceStore nonceStore,
      KafkaStreams kafkaStreams,
      LicenseManager licenseManager,
      KeyTrustPolicy keyTrustPolicy) {
    this.config = config;
    this.globalConfigStore = globalConfigStore;
    this.nonceStore = nonceStore;
    this.kafkaStreams = kafkaStreams;
    this.licenseManager = licenseManager;
    this.keyTrustPolicy = keyTrustPolicy;
    this.validator = new AuthorizationTokenValidator(publicKeyProvider);
  }

  /** Test constructor — no CDI, license check always permits authorization. */
  EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      PublicKeyProvider publicKeyProvider,
      NonceStore nonceStore,
      KafkaStreams kafkaStreams) {
    this(
        config,
        globalConfigStore,
        publicKeyProvider,
        nonceStore,
        kafkaStreams,
        new AlwaysAllowLicenseManager(),
        new OpenKeyTrustPolicy());
  }

  /** Minimal LicenseManager used only by the no-CDI test constructor above. */
  private static final class AlwaysAllowLicenseManager implements LicenseManager {
    @Override
    public LicenseState getLicenseState() {
      return LicenseState.VALID;
    }

    @Override
    public String getLicenseInfo() {
      return "test";
    }

    @Override
    public int getPartitionBudget() {
      return Integer.MAX_VALUE;
    }

    @Override
    public boolean isEventSigningAllowed() {
      return true;
    }

    @Override
    public boolean isEngineRequiresAuthorization() {
      return true;
    }

    @Override
    public void updateFromLicensePush(
        String licenseType,
        Integer partitionBudget,
        boolean eventSigning,
        boolean commandAuthorization) {}
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
   * Authorises an incoming command on {@code process-instance-trigger} and returns structured trust
   * metadata to be attached to the command/update chain.
   */
  public CommandTrustMetadataDTO authorize(
      Headers headers, ProcessInstanceTriggerEnvelope triggerEnvelope) {
    ProcessInstanceTriggerDTO trigger = triggerEnvelope.trigger();
    GlobalConfigurationDTO config = effectiveConfig();
    if (!config.isEngineRequiresAuthorization()) {
      return null;
    }
    if (!licenseManager.isEngineRequiresAuthorization()) {
      log.warn(
          "engineRequiresAuthorization=true in runtime config but the active license does not permit"
              + " command authorization — command accepted without validation");
      return null;
    }

    Header authHeader = lastHeader(headers, AUTH_HEADER);
    Header sigHeader = lastHeader(headers, SIG_HEADER);

    boolean isEntryCommand =
        trigger instanceof StartCommandDTO || trigger instanceof AbortTriggerDTO;

    if (isEntryCommand) {
      // Path A: JWT present → validate JWT (external client path)
      if (authHeader != null && authHeader.value() != null) {
        return authorizeViaJwt(authHeader, trigger);
      }
      // Path B: Ed25519 signature present → check key role
      if (sigHeader != null && sigHeader.value() != null) {
        return authorizeEntryCommandViaEd25519(sigHeader, triggerEnvelope);
      }
      // Path C: neither JWT nor signature → reject
      throw new AuthorizationTokenException(
          "Entry command "
              + trigger.getClass().getSimpleName()
              + " requires "
              + AUTH_HEADER
              + " (JWT) or "
              + SIG_HEADER
              + " from an ENGINE-role key");
    }

    // Non-entry commands: existing Ed25519/signing flow (unchanged)
    if (sigHeader != null && sigHeader.value() != null) {
      return authorizeViaEd25519(sigHeader, triggerEnvelope);
    }

    if (config.isSigningEnabled()) {
      throw new AuthorizationTokenException(
          "Missing required "
              + SIG_HEADER
              + " header on command "
              + trigger.getClass().getSimpleName());
    }

    return trigger.getCurrentTrustMetadata();
  }

  // ── Entry command via Ed25519 ─────────────────────────────────────────────

  /**
   * Authorizes an entry command (StartCommandDTO / AbortTriggerDTO) via Ed25519 signature when the
   * signing key has a role that is trusted for ENGINE operations.
   */
  private CommandTrustMetadataDTO authorizeEntryCommandViaEd25519(
      Header sigHeader, ProcessInstanceTriggerEnvelope triggerEnvelope) {
    if (triggerEnvelope.hasSignatureError()) {
      throw new AuthorizationTokenException(triggerEnvelope.signatureError());
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    String keyId = dot >= 0 ? headerValue.substring(0, dot) : headerValue;

    SigningKeyDTO entry = lookupSigningKey(keyId);
    if (entry == null) {
      throw new AuthorizationTokenException(
          "Unknown Ed25519 keyId '"
              + keyId
              + "' — rejecting entry command "
              + triggerEnvelope.trigger().getClass().getSimpleName());
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException(
          "Revoked Ed25519 keyId '"
              + keyId
              + "' — rejecting entry command "
              + triggerEnvelope.trigger().getClass().getSimpleName());
    }
    if (!triggerEnvelope.signatureVerified()) {
      throw new AuthorizationTokenException(
          "Ed25519 header present for entry command "
              + triggerEnvelope.trigger().getClass().getSimpleName()
              + " but the signature was not verified by the deserializer");
    }
    if (!keyTrustPolicy.isTrustedForRole(entry, KeyRole.ENGINE)) {
      throw new AuthorizationTokenException(
          "Ed25519 keyId '"
              + keyId
              + "' has role "
              + entry.effectiveRole()
              + " which is not trusted for entry commands (requires ENGINE or PLATFORM)");
    }

    log.info(
        "✅ Authorised (Ed25519/ENGINE) command={} keyId={} owner={}",
        triggerEnvelope.trigger().getClass().getSimpleName(),
        keyId,
        entry.getOwner());

    return CommandTrustMetadataDTO.builder()
        .authMethod(CommandAuthMethod.ED25519)
        .verificationResult(CommandTrustVerificationResult.ENGINE_SIGNED)
        .trusted(true)
        .signerKeyId(keyId)
        .signerOwner(entry.getOwner())
        .signerAlgorithm(entry.getAlgorithm())
        .build();
  }

  // ── JWT path ────────────────────────────────────────────────────────────────

  private CommandTrustMetadataDTO authorizeViaJwt(
      Header authHeader, ProcessInstanceTriggerDTO trigger) {
    String rawJwt = new String(authHeader.value(), StandardCharsets.UTF_8);
    TokenClaims claims = validator.validate(rawJwt);
    validateClaimsMatchCommand(claims, trigger);
    if (!nonceStore.checkAndRecord(claims.getAuditId())) {
      throw new AuthorizationTokenException("Replayed auditId detected: " + claims.getAuditId());
    }
    log.info(
        "✅ Authorised (JWT) command={} user={} auditId={}",
        trigger.getClass().getSimpleName(),
        claims.getUserId(),
        claims.getAuditId());
    return CommandTrustMetadataDTO.builder()
        .authMethod(CommandAuthMethod.JWT)
        .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
        .trusted(true)
        .userId(claims.getUserId())
        .issuer(claims.getIssuer())
        .build();
  }

  // ── Ed25519 path ──────────────────────────────────────────────────────────────

  /**
   * Enforces Ed25519 authorization for worker responses and engine-internal commands.
   *
   * <p>The deserializer has already verified the signature cryptographically. This method enforces
   * that the referenced key still exists in the {@code taktx-signing-keys} KTable and is not
   * revoked.
   */
  private CommandTrustMetadataDTO authorizeViaEd25519(
      Header sigHeader, ProcessInstanceTriggerEnvelope triggerEnvelope) {
    if (triggerEnvelope.hasSignatureError()) {
      throw new AuthorizationTokenException(triggerEnvelope.signatureError());
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    String keyId = dot >= 0 ? headerValue.substring(0, dot) : headerValue;

    SigningKeyDTO entry = lookupSigningKey(keyId);
    if (entry == null) {
      throw new AuthorizationTokenException(
          "Unknown Ed25519 keyId '"
              + keyId
              + "' — rejecting command "
              + triggerEnvelope.trigger().getClass().getSimpleName());
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException(
          "Revoked Ed25519 keyId '"
              + keyId
              + "' — rejecting command "
              + triggerEnvelope.trigger().getClass().getSimpleName());
    }
    if (!triggerEnvelope.signatureVerified()) {
      throw new AuthorizationTokenException(
          "Ed25519 header present for command "
              + triggerEnvelope.trigger().getClass().getSimpleName()
              + " but the signature was not verified by the deserializer");
    }
    log.info(
        "✅ Authorised (Ed25519) command={} keyId={} owner={}",
        triggerEnvelope.trigger().getClass().getSimpleName(),
        keyId,
        entry.getOwner());

    return CommandTrustMetadataDTO.builder()
        .authMethod(CommandAuthMethod.ED25519)
        .verificationResult(CommandTrustVerificationResult.SIGNATURE_VERIFIED)
        .trusted(true)
        .signerKeyId(keyId)
        .signerOwner(entry.getOwner())
        .signerAlgorithm(entry.getAlgorithm())
        .build();
  }

  private GlobalConfigurationDTO effectiveConfig() {
    if (globalConfigStore == null || globalConfigStore.get() == null) {
      return GlobalConfigurationDTO.builder().build();
    }
    return globalConfigStore.get();
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

  private static Header lastHeader(Headers headers, String headerName) {
    return headers != null ? headers.lastHeader(headerName) : null;
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
