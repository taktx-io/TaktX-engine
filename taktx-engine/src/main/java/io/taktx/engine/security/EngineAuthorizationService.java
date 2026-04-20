/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.quarkus.runtime.Startup;
import io.taktx.Topics;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.Constants;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TokenClaims;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
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
  private final KeyTrustPolicy keyTrustPolicy;
  private final MessageSecurityPolicyRegistry messageSecurityPolicyRegistry;

  private ReadOnlyKeyValueStore<String, SigningKeyDTO> signingKeysStore;

  @Inject
  public EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      PublicKeyProvider publicKeyProvider,
      NonceStore nonceStore,
      KafkaStreams kafkaStreams,
      KeyTrustPolicy keyTrustPolicy,
      MessageSecurityPolicyRegistry messageSecurityPolicyRegistry) {
    this.config = config;
    this.globalConfigStore = globalConfigStore;
    this.nonceStore = nonceStore;
    this.kafkaStreams = kafkaStreams;
    this.keyTrustPolicy = keyTrustPolicy;
    this.messageSecurityPolicyRegistry = messageSecurityPolicyRegistry;
    this.validator = new AuthorizationTokenValidator(publicKeyProvider);
  }

  /** Test constructor — no CDI. */
  EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      PublicKeyProvider publicKeyProvider,
      NonceStore nonceStore,
      KafkaStreams kafkaStreams,
      KeyTrustPolicy keyTrustPolicy) {
    this(
        config,
        globalConfigStore,
        publicKeyProvider,
        nonceStore,
        kafkaStreams,
        keyTrustPolicy,
        new MessageSecurityPolicyRegistry());
  }

  /** Test constructor — no CDI. */
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
        new OpenKeyTrustPolicy(),
        new MessageSecurityPolicyRegistry());
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
   *
   * <p>Two independent security gates are evaluated and both must pass when both are active:
   *
   * <ul>
   *   <li><b>Authorization gate</b> ({@code engineRequiresAuthorization} config): applies to entry
   *       commands; satisfied by a valid JWT <em>or</em> an ENGINE-role Ed25519 key. ENGINE-role
   *       keys implicitly carry authorization because only the engine itself generates them (e.g.
   *       sub-process / call-activity triggers).
   *   <li><b>Signing gate</b> ({@code signingEnabled} config): applies to <em>all</em> commands
   *       including entry; satisfied by any valid Ed25519 signature (CLIENT or ENGINE role).
   * </ul>
   *
   * <p>When both gates are active an external entry command must carry <em>both</em> a JWT
   * (authorization) and an Ed25519 signature (authenticity). An ENGINE-role Ed25519 alone satisfies
   * both gates, so engine-internal entry commands continue to work without a JWT.
   */
  public CommandTrustMetadataDTO authorize(
      Headers headers, ProcessInstanceTriggerEnvelope triggerEnvelope) {
    ProcessInstanceTriggerDTO trigger = triggerEnvelope.trigger();
    GlobalConfigurationDTO cfg = effectiveConfig();
    MessageSecurityPolicy policy = resolveProcessInstancePolicy(trigger);

    Header authHeader = lastHeader(headers, AUTH_HEADER);
    Header sigHeader = lastHeader(headers, SIG_HEADER);

    boolean isEntryCommand = policy.requireJwt();

    // ── Entry commands: AND-logic across both gates
    // ───────────────────────────────────────────────
    if (isEntryCommand) {
      boolean authActive = cfg.isEngineRequiresAuthorization();
      boolean signingActive = cfg.isSigningEnabled();

      if (!authActive && !signingActive) {
        return null;
      }

      // Verify JWT if present (throws on invalid token; a presented JWT must always be valid)
      CommandTrustMetadataDTO jwtMeta = null;
      if (authHeader != null && authHeader.value() != null) {
        jwtMeta = authorizeViaJwt(authHeader, trigger);
      }

      // Verify Ed25519 if present; accepts CLIENT- and ENGINE-role keys
      CommandTrustMetadataDTO sigMeta = null;
      boolean sigIsEngine = false;
      if (sigHeader != null && sigHeader.value() != null) {
        sigMeta = authorizeViaEd25519(sigHeader, triggerEnvelope, requiredRole(policy));
        sigIsEngine =
            CommandTrustVerificationResult.ENGINE_SIGNED == sigMeta.getVerificationResult();
      }

      // Auth gate: JWT or ENGINE-role Ed25519 satisfies it
      if (authActive
          && policy.allowEngineSignatureAsJwtEquivalent()
          && jwtMeta == null
          && !sigIsEngine) {
        throw new AuthorizationTokenException(
            "Entry command "
                + trigger.getClass().getSimpleName()
                + " requires "
                + AUTH_HEADER
                + " (JWT) or "
                + SIG_HEADER
                + " from an ENGINE-role key");
      }

      // Signing gate: any valid Ed25519 satisfies it
      if (signingActive && policy.requireSignature() && sigMeta == null) {
        throw new AuthorizationTokenException(
            "Entry command "
                + trigger.getClass().getSimpleName()
                + " requires "
                + SIG_HEADER
                + " (signingEnabled=true)");
      }

      // Both JWT and Ed25519 verified — combine: JWT provides auth context, enrich with signer info
      if (jwtMeta != null && sigMeta != null) {
        return CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT_AND_ED25519)
            .verificationResult(jwtMeta.getVerificationResult())
            .trusted(true)
            .userId(jwtMeta.getUserId())
            .issuer(jwtMeta.getIssuer())
            .signerKeyId(sigMeta.getSignerKeyId())
            .signerOwner(sigMeta.getSignerOwner())
            .signerAlgorithm(sigMeta.getSignerAlgorithm())
            .build();
      }
      return jwtMeta != null ? jwtMeta : sigMeta;
    }

    KeyRole requiredRole = requiredRole(policy);

    // ── Gate 2: Non-entry command Ed25519 signing
    // ─────────────────────────────────────────────────
    boolean authActive = cfg.isEngineRequiresAuthorization();
    boolean signingActive = cfg.isSigningEnabled();

    if (sigHeader != null && sigHeader.value() != null) {
      if (authActive || signingActive) {
        return authorizeViaEd25519(sigHeader, triggerEnvelope, requiredRole);
      }
    }

    if (authActive || signingActive) {
      throw new AuthorizationTokenException(
          "Missing required "
              + SIG_HEADER
              + " header on command "
              + trigger.getClass().getSimpleName()
              + " — trusted "
              + requiredRole
              + " signature required when process-instance security is active");
    }

    return null;
  }

  /**
   * Authorizes a `topic-meta-requested` record after the deserializer has already verified the
   * Ed25519 signature cryptographically.
   *
   * <p>This path derives trust exclusively from the signing-key KTable and active trust policy. A
   * request must carry a valid `X-TaktX-Signature` whose key resolves to a trusted `CLIENT`-or-
   * higher role.
   */
  public SigningKeyDTO authorizeTopicMetaRequest(Headers headers, TopicMetaDTO request) {
    MessageSecurityPolicy policy =
        messageSecurityPolicyRegistry.resolve(
            Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName(), TopicMetaDTO.class);
    Header sigHeader = lastHeader(headers, SIG_HEADER);
    if (sigHeader == null || sigHeader.value() == null) {
      throw new AuthorizationTokenException(
          "Missing required "
              + SIG_HEADER
              + " header on topic-meta-requested for topic "
              + (request == null ? null : request.getTopicName()));
    }

    String keyId = extractKeyId(sigHeader);
    SigningKeyDTO entry = lookupSigningKey(keyId);
    if (entry == null) {
      throw new AuthorizationTokenException(
          "Unknown Ed25519 keyId '" + keyId + "' — rejecting topic-meta-requested record");
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException(
          "Revoked Ed25519 keyId '" + keyId + "' — rejecting topic-meta-requested record");
    }
    if (!keyTrustPolicy.isTrustedForRole(entry, requiredRole(policy))) {
      throw new AuthorizationTokenException(
          "Signing keyId '"
              + keyId
              + "' is not trusted for CLIENT topic requests — rejecting topic-meta-requested record");
    }

    log.info(
        "✅ Authorised topic-meta-requested topic={} keyId={} owner={} role={}",
        request == null ? null : request.getTopicName(),
        keyId,
        entry.getOwner(),
        entry.effectiveRole());
    return entry;
  }

  /**
   * Authorizes a {@code schedule-commands} record after the deserializer has already verified the
   * Ed25519 signature cryptographically.
   *
   * <p>This path requires a valid {@code X-TaktX-Signature} whose signing key resolves to a trusted
   * {@code ENGINE} role.
   */
  public SigningKeyDTO authorizeScheduleCommand(
      Headers headers, ScheduleKeyDTO scheduleKey, MessageScheduleDTO schedule) {
    MessageSecurityPolicy policy =
        messageSecurityPolicyRegistry.resolve(
            Topics.SCHEDULE_COMMANDS.getTopicName(), MessageScheduleDTO.class);
    Header sigHeader = lastHeader(headers, SIG_HEADER);
    if (sigHeader == null || sigHeader.value() == null) {
      throw new AuthorizationTokenException(
          "Missing required "
              + SIG_HEADER
              + " header on schedule-commands for scheduleKey "
              + scheduleKey);
    }

    String keyId = extractKeyId(sigHeader);
    SigningKeyDTO entry = lookupSigningKey(keyId);
    if (entry == null) {
      throw new AuthorizationTokenException(
          "Unknown Ed25519 keyId '" + keyId + "' — rejecting schedule-commands record");
    }
    if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException(
          "Revoked Ed25519 keyId '" + keyId + "' — rejecting schedule-commands record");
    }
    if (!keyTrustPolicy.isTrustedForRole(entry, requiredRole(policy))) {
      throw new AuthorizationTokenException(
          "Signing keyId '"
              + keyId
              + "' is not trusted for ENGINE schedule commands — rejecting schedule-commands record");
    }

    log.info(
        "✅ Authorised schedule-commands scheduleKey={} keyId={} owner={} role={} messageType={}",
        scheduleKey,
        keyId,
        entry.getOwner(),
        entry.effectiveRole(),
        scheduleMessageType(schedule));
    return entry;
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
      Header sigHeader, ProcessInstanceTriggerEnvelope triggerEnvelope, KeyRole requiredRole) {
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
    if (!keyTrustPolicy.isTrustedForRole(entry, requiredRole)) {
      throw new AuthorizationTokenException(
          "Signing keyId '"
              + keyId
              + "' is not trusted for "
              + requiredRole
              + " process-instance command "
              + triggerEnvelope.trigger().getClass().getSimpleName());
    }
    boolean isEngine = keyTrustPolicy.isTrustedForRole(entry, KeyRole.ENGINE);
    log.info(
        "✅ Authorised (Ed25519) command={} keyId={} owner={} roleRequired={} derivedRole={}",
        triggerEnvelope.trigger().getClass().getSimpleName(),
        keyId,
        entry.getOwner(),
        requiredRole,
        entry.effectiveRole());

    return CommandTrustMetadataDTO.builder()
        .authMethod(CommandAuthMethod.ED25519)
        .verificationResult(
            isEngine
                ? CommandTrustVerificationResult.ENGINE_SIGNED
                : CommandTrustVerificationResult.SIGNATURE_VERIFIED)
        .trusted(true)
        .signerKeyId(keyId)
        .signerOwner(entry.getOwner())
        .signerAlgorithm(entry.getAlgorithm())
        .build();
  }

  private MessageSecurityPolicy resolveProcessInstancePolicy(ProcessInstanceTriggerDTO trigger) {
    try {
      return messageSecurityPolicyRegistry.resolve(
          Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(), trigger.getClass());
    } catch (IllegalStateException e) {
      throw new AuthorizationTokenException(
          "Unsupported process-instance trigger type "
              + trigger.getClass().getSimpleName()
              + " for authorization policy evaluation",
          e);
    }
  }

  private static KeyRole requiredRole(MessageSecurityPolicy policy) {
    KeyRole requiredRole = policy.minimumAllowedRole();
    if (requiredRole == null) {
      throw new AuthorizationTokenException(
          "Message security policy has no allowed signed roles for topic='"
              + policy.topicName()
              + "' messageClass='"
              + policy.messageClass().getSimpleName()
              + "'");
    }
    return requiredRole;
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

  private static String extractKeyId(Header sigHeader) {
    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    return dot >= 0 ? headerValue.substring(0, dot) : headerValue;
  }

  private static String scheduleMessageType(MessageScheduleDTO schedule) {
    if (schedule == null || schedule.getMessage() == null) {
      return null;
    }
    return schedule.getMessage().getClass().getSimpleName();
  }

  private void validateClaimsMatchCommand(TokenClaims claims, ProcessInstanceTriggerDTO trigger) {
    switch (trigger) {
      case StartCommandDTO start -> {
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
      }
      case AbortTriggerDTO _ when !"CANCEL".equals(claims.getAction()) -> {
        throw new AuthorizationTokenException(
            "Token action '" + claims.getAction() + "' does not match CANCEL command");
      }
      case SetVariableTriggerDTO _ when !"SET_VARIABLE".equals(claims.getAction()) -> {
        throw new AuthorizationTokenException(
            "Token action '" + claims.getAction() + "' does not match SET_VARIABLE command");
      }
      default ->
          log.debug(
              "No claim matching defined for {}, allowing", trigger.getClass().getSimpleName());
    }
  }
}
