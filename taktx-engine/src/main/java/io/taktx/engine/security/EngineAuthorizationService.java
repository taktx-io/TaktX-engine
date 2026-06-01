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
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.Constants;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TokenClaims;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.NamespaceSecurityPolicyStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.MessageEventIngressEnvelope;
import io.taktx.engine.pd.SignalIngressEnvelope;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.engine.topicmanagement.TopicMetaIngressEnvelope;
import io.taktx.security.AuthorizationTokenException;
import io.taktx.security.AuthorizationTokenValidator;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaStreams;

/**
 * Validates incoming Kafka commands and worker responses.
 *
 * <p>Two validation paths:
 *
 * <ul>
 *   <li>{@code tx-auth} (RS256 JWT) — used by Console/Platform for start-process and abort
 *       commands; validates claims and expiry. Durable replay protection is enforced by {@link
 *       ReplayProtectionProcessor} upstream in the Kafka Streams topology.
 *   <li>{@code tx-sig} (Ed25519) — used by worker processes for task responses and by the engine
 *       itself for internal sub-process/call-activity triggers. Key resolution, revoke checks, and
 *       trust-policy evaluation are delegated to {@link VerificationCore}.
 * </ul>
 *
 * <p>When legacy authorization/signing gates are disabled in the latest {@link
 * GlobalConfigurationDTO}, unsigned ingress is accepted. However, a presented {@code tx-auth} JWT
 * is still validated and surfaced as optional trust/user context.
 */
@ApplicationScoped
@Startup
@Slf4j
public class EngineAuthorizationService {

  static final String AUTH_HEADER = Constants.HEADER_AUTHORIZATION;
  static final String SIG_HEADER = Constants.HEADER_ENGINE_SIGNATURE;

  private final TaktConfiguration config;
  private final GlobalConfigStore globalConfigStore;
  private final NamespaceSecurityPolicyStore namespaceSecurityPolicyStore;
  private final AuthorizationTokenValidator validator;
  private final KeyTrustPolicy keyTrustPolicy;
  private final MessageSecurityPolicyRegistry messageSecurityPolicyRegistry;
  private final VerificationCore verificationCore;

  @Inject
  public EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      PublicKeyProvider publicKeyProvider,
      KeyTrustPolicy keyTrustPolicy,
      MessageSecurityPolicyRegistry messageSecurityPolicyRegistry,
      VerificationCore verificationCore) {
    this.config = config;
    this.globalConfigStore = globalConfigStore;
    this.namespaceSecurityPolicyStore = namespaceSecurityPolicyStore;
    this.keyTrustPolicy = keyTrustPolicy;
    this.messageSecurityPolicyRegistry = messageSecurityPolicyRegistry;
    this.verificationCore = verificationCore;
    this.validator = new AuthorizationTokenValidator(publicKeyProvider);
  }

  /** Test constructor — no CDI. */
  EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      PublicKeyProvider publicKeyProvider,
      KafkaStreams kafkaStreams,
      KeyTrustPolicy keyTrustPolicy,
      MessageSecurityPolicyRegistry messageSecurityPolicyRegistry) {
    this(
        config,
        globalConfigStore,
        namespaceSecurityPolicyStore,
        publicKeyProvider,
        keyTrustPolicy,
        messageSecurityPolicyRegistry,
        new VerificationCore(config, kafkaStreams, keyTrustPolicy));
  }

  /** Test constructor preserving the pre-policy API by using an empty namespace policy store. */
  EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      PublicKeyProvider publicKeyProvider,
      KafkaStreams kafkaStreams,
      KeyTrustPolicy keyTrustPolicy,
      MessageSecurityPolicyRegistry messageSecurityPolicyRegistry) {
    this(
        config,
        globalConfigStore,
        new NamespaceSecurityPolicyStore(),
        publicKeyProvider,
        kafkaStreams,
        keyTrustPolicy,
        messageSecurityPolicyRegistry);
  }

  /** Test constructor — no CDI. */
  EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      PublicKeyProvider publicKeyProvider,
      KafkaStreams kafkaStreams,
      KeyTrustPolicy keyTrustPolicy) {
    this(
        config,
        globalConfigStore,
        namespaceSecurityPolicyStore,
        publicKeyProvider,
        keyTrustPolicy,
        new MessageSecurityPolicyRegistry(),
        new VerificationCore(config, kafkaStreams, keyTrustPolicy));
  }

  /** Test constructor preserving the pre-policy API by using an empty namespace policy store. */
  EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      PublicKeyProvider publicKeyProvider,
      KafkaStreams kafkaStreams,
      KeyTrustPolicy keyTrustPolicy) {
    this(
        config,
        globalConfigStore,
        new NamespaceSecurityPolicyStore(),
        publicKeyProvider,
        kafkaStreams,
        keyTrustPolicy);
  }

  /** Test constructor — no CDI. Accessible from any package (e.g. integration test classes). */
  public EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      NamespaceSecurityPolicyStore namespaceSecurityPolicyStore,
      PublicKeyProvider publicKeyProvider,
      KafkaStreams kafkaStreams) {
    this(
        config,
        globalConfigStore,
        namespaceSecurityPolicyStore,
        publicKeyProvider,
        new OpenKeyTrustPolicy(),
        new MessageSecurityPolicyRegistry(),
        new VerificationCore(config, kafkaStreams));
  }

  /** Test constructor preserving the pre-policy API by using an empty namespace policy store. */
  public EngineAuthorizationService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      PublicKeyProvider publicKeyProvider,
      KafkaStreams kafkaStreams) {
    this(
        config,
        globalConfigStore,
        new NamespaceSecurityPolicyStore(),
        publicKeyProvider,
        kafkaStreams);
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
   * io.taktx.serdes.ProtoDeserializer} via {@link EngineSigningKeysHolder}.
   */
  private String resolvePublicKeyFromKTable(String keyId) {
    SigningKeyDTO entry = verificationCore.resolveKey(keyId);
    if (entry == null || entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) return null;
    return entry.getPublicKeyBase64();
  }

  /**
   * Authorises an incoming command on {@code process-instance-trigger} and returns structured trust
   * metadata to be attached to the command/update chain.
   *
   * <p>Two process-instance security inputs may apply:
   *
   * <ul>
   *   <li><b>Optional JWT context</b> ({@code tx-auth}): when present on an entry or
   *       task-completion command it is always validated and surfaced as trust/user context.
   *   <li><b>Signature gate</b>: applies whenever legacy signing is enabled <em>or</em> the
   *       authoritative namespace policy is {@code ANCHORED}. It is satisfied by a valid trusted
   *       Ed25519 signature for the message type's minimum role.
   * </ul>
   *
   * <p>Under the simplified authoritative policy model, namespace mode no longer requires JWTs as a
   * posture mechanism: {@code OPEN} accepts unsigned ingress, while {@code ANCHORED} requires a
   * trusted signature. JWTs remain optional business/user context.
   */
  public CommandTrustMetadataDTO authorize(
      Headers headers, ProcessInstanceTriggerEnvelope triggerEnvelope) {
    ProcessInstanceTriggerDTO trigger = triggerEnvelope.trigger();
    if (trigger == null) {
      // Trigger could not be decoded. Skip authorization — the ProcessInstanceProcessor
      // null-trigger guard will emit a PAYLOAD_DESERIALIZATION_ERROR DLQ entry.
      return null;
    }
    GlobalConfigurationDTO cfg = effectiveConfig();
    MessageSecurityPolicy policy = resolveProcessInstancePolicy(trigger);
    NamespaceSecurityPolicyDTO authoritativePolicy = authoritativePolicy();
    assertTrustAnchorRequirementSatisfied(authoritativePolicy);

    Header authHeader = lastHeader(headers, AUTH_HEADER);
    Header sigHeader = lastHeader(headers, SIG_HEADER);

    boolean entryCommand = isEntryCommand(trigger);
    boolean taskCompletionTrigger = isTaskCompletionTrigger(trigger);
    boolean signingActive = isSignatureGateActive(cfg, authoritativePolicy);

    KeyRole requiredRole = requiredRole(policy);

    // Verify JWT if present (throws on invalid token; a presented JWT must always be valid).
    // JWT remains optional business/user context even when no legacy runtime gate is active.
    CommandTrustMetadataDTO jwtMeta = null;
    if ((entryCommand || taskCompletionTrigger)
        && authHeader != null
        && authHeader.value() != null) {
      jwtMeta = authorizeViaJwt(authHeader, triggerEnvelope);
    }

    if (!signingActive && jwtMeta == null) {
      return null;
    }

    // Verify Ed25519 if present when any security mechanism is active.
    CommandTrustMetadataDTO sigMeta = null;
    boolean sigIsEngine = false;
    if (sigHeader != null && sigHeader.value() != null) {
      sigMeta = authorizeViaEd25519(sigHeader, triggerEnvelope, requiredRole);
      sigIsEngine =
          CommandTrustVerificationResult.ENGINE_SIGNED == sigMeta.getVerificationResult();
    }

    if (signingActive && policy.requireSignature() && sigMeta == null) {
      if (entryCommand) {
        throw new AuthorizationTokenException(
            "Entry command "
                + trigger.getClass().getSimpleName()
                + " requires "
                + SIG_HEADER
                + " (trusted signature required)");
      }
      throw new AuthorizationTokenException(
          "Missing required "
              + SIG_HEADER
              + " header on command "
              + trigger.getClass().getSimpleName()
              + " — trusted "
              + requiredRole
              + " signature required when process-instance security is active");
    }

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

  /** Authorizes externally published {@code topic-meta-requested} ingress. */
  public SigningKeyDTO authorizeTopicMetaIngress(
      Headers headers, TopicMetaIngressEnvelope ingressEnvelope) {
    return authorizeSignedIngress(
        headers,
        ingressEnvelope == null ? null : ingressEnvelope.value(),
        ingressEnvelope == null ? false : ingressEnvelope.signatureVerified(),
        ingressEnvelope == null ? null : ingressEnvelope.signatureError(),
        Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName());
  }

  public SigningKeyDTO authorizeMessageEventIngress(
      Headers headers, MessageEventIngressEnvelope ingressEnvelope) {
    return authorizeSignedIngress(
        headers,
        ingressEnvelope == null ? null : ingressEnvelope.value(),
        ingressEnvelope == null ? false : ingressEnvelope.signatureVerified(),
        ingressEnvelope == null ? null : ingressEnvelope.signatureError(),
        Topics.MESSAGE_EVENT_TOPIC.getTopicName());
  }

  public SigningKeyDTO authorizeSignalIngress(Headers headers, SignalIngressEnvelope ingressEnvelope) {
    return authorizeSignedIngress(
        headers,
        ingressEnvelope == null ? null : ingressEnvelope.value(),
        ingressEnvelope == null ? false : ingressEnvelope.signatureVerified(),
        ingressEnvelope == null ? null : ingressEnvelope.signatureError(),
        Topics.SIGNAL_TOPIC.getTopicName());
  }

  /**
   * Authorizes a {@code schedule-commands} record after the deserializer has already verified the
   * Ed25519 signature cryptographically.
   *
   * <p>Returns {@code null} when all security gates are disabled (both {@code signingEnabled} and
   * {@code engineRequiresAuthorization} are {@code false} in the latest {@link
   * GlobalConfigurationDTO}). This matches the opt-in posture of {@link #authorize}: no enforcement
   * is applied until an operator explicitly enables it via the {@code taktx-configuration} topic.
   *
   * <p>When security is active, delegates key resolution, revoke check, and trust-policy evaluation
   * to {@link VerificationCore}. Requires a valid {@code tx-sig} whose signing key resolves to a
   * trusted {@code ENGINE} role.
   */
  public SigningKeyDTO authorizeScheduleCommand(
      Headers headers, ScheduleKeyDTO scheduleKey, MessageScheduleDTO schedule) {
    GlobalConfigurationDTO cfg = effectiveConfig();
    MessageSecurityPolicy policy =
        messageSecurityPolicyRegistry.resolve(
            Topics.SCHEDULE_COMMANDS.getTopicName(), MessageScheduleDTO.class);
    NamespaceSecurityPolicyDTO authoritativePolicy = authoritativePolicy();
    assertTrustAnchorRequirementSatisfied(authoritativePolicy);
    if (!isSignatureGateActive(cfg, authoritativePolicy)) {
      log.debug("Security gates disabled — skipping signature enforcement for schedule-commands");
      return null;
    }

    VerifiedMessageContext ctx =
        verificationCore.verify(lastHeader(headers, SIG_HEADER), requiredRole(policy));

    log.info(
        "✅ Authorised schedule-commands scheduleKey={} keyId={} owner={} role={} messageType={}",
        scheduleKey,
        ctx.keyId(),
        ctx.key().getOwner(),
        ctx.role(),
        scheduleMessageType(schedule));
    return ctx.key();
  }

  /**
   * Authorizes an authoritative namespace-security-policy mutation.
   *
   * <p>Unlike protected runtime topics, authoritative control-plane mutation must never fall back
   * to legacy opt-in runtime flags. A valid {@code tx-sig} from a trusted {@code PLATFORM} signer
   * is always required, and the signature is verified directly against the raw payload bytes (or an
   * empty payload for tombstones).
   */
  public SigningKeyDTO authorizeNamespaceSecurityPolicyMutation(Headers headers, byte[] payload) {
    Header sigHeader = lastHeader(headers, SIG_HEADER);
    if (sigHeader == null || sigHeader.value() == null) {
      throw new AuthorizationTokenException(
          "Missing required "
              + SIG_HEADER
              + " header on authoritative namespace security policy mutation");
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      throw new AuthorizationTokenException(
          "Malformed "
              + SIG_HEADER
              + " header (expected '<keyId>.<base64sig>') on authoritative namespace security policy mutation");
    }

    String keyId = headerValue.substring(0, dot);
    String base64Sig = headerValue.substring(dot + 1);
    SigningKeyDTO key = verificationCore.resolveKey(keyId);
    if (key == null) {
      throw new AuthorizationTokenException(
          "Unknown Ed25519 keyId '"
              + keyId
              + "' — rejecting authoritative namespace security policy mutation");
    }
    if (key.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
      throw new AuthorizationTokenException(
          "Revoked Ed25519 keyId '"
              + keyId
              + "' — rejecting authoritative namespace security policy mutation");
    }
    if (!keyTrustPolicy.isTrustedForRole(key, KeyRole.PLATFORM)) {
      throw new AuthorizationTokenException(
          "Signing keyId '"
              + keyId
              + "' (role="
              + key.effectiveRole()
              + ") is not trusted for required role "
              + KeyRole.PLATFORM);
    }

    try {
      byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
      byte[] payloadToVerify = payload != null ? payload : new byte[0];
      if (!Ed25519Service.verify(payloadToVerify, signatureBytes, key.getPublicKeyBase64())) {
        throw new AuthorizationTokenException(
            "Ed25519 signature verification failed for authoritative namespace security policy mutation keyId="
                + keyId);
      }
    } catch (IllegalArgumentException e) {
      throw new AuthorizationTokenException(
          "Malformed base64 signature for authoritative namespace security policy mutation keyId="
              + keyId
              + ": "
              + e.getMessage());
    }

    log.info(
        "✅ Authorised namespace-security-policy mutation keyId={} owner={} role={}",
        keyId,
        key.getOwner(),
        key.effectiveRole());
    return key;
  }

  private SigningKeyDTO authorizeSignedIngress(
      Headers headers,
      Object message,
      boolean signatureVerified,
      String signatureError,
      String topicName) {
    if (message == null) {
      return null;
    }

    GlobalConfigurationDTO cfg = effectiveConfig();
    NamespaceSecurityPolicyDTO authoritativePolicy = authoritativePolicy();
    assertTrustAnchorRequirementSatisfied(authoritativePolicy);
    if (!isSignatureGateActive(cfg, authoritativePolicy)) {
      return null;
    }

    MessageSecurityPolicy policy = messageSecurityPolicyRegistry.resolve(topicName, message.getClass());
    if (signatureError != null && !signatureError.isBlank()) {
      throw new AuthorizationTokenException(signatureError);
    }

    VerifiedMessageContext ctx = verificationCore.verify(lastHeader(headers, SIG_HEADER), requiredRole(policy));
    if (!signatureVerified) {
      throw new AuthorizationTokenException(
          "Ed25519 header present for ingress message "
              + message.getClass().getSimpleName()
              + " but the signature was not verified by the deserializer");
    }
    return ctx.key();
  }

  // ── JWT path ────────────────────────────────────────────────────────────────

  public TokenClaims validateJwtClaims(Header authHeader, ProcessInstanceTriggerDTO trigger) {
    String rawJwt = new String(authHeader.value(), StandardCharsets.UTF_8);
    TokenClaims claims = validator.validate(rawJwt);
    validateClaimsMatchCommand(claims, trigger);
    return claims;
  }

  public ReplayProtectionMode replayProtectionMode() {
    ReplayProtectionMode mode = effectiveConfig().getReplayProtectionMode();
    return mode != null ? mode : ReplayProtectionMode.COMPAT;
  }

  public long replayProtectionRetentionMs() {
    long retentionMs = effectiveConfig().getReplayProtectionRetentionMs();
    return retentionMs > 0 ? retentionMs : 600_000L;
  }

  public boolean isReplayProtectionActive() {
    return replayProtectionMode() != ReplayProtectionMode.OFF;
  }

  public String canonicalReplayKey(TokenClaims claims) {
    if (claims == null || claims.getAuditId() == null || claims.getAuditId().isBlank()) {
      return null;
    }
    String issuer =
        claims.getIssuer() == null || claims.getIssuer().isBlank() ? "unknown" : claims.getIssuer();
    return config.getTenantId()
        + ":"
        + config.getNamespace()
        + ":"
        + issuer
        + ":"
        + claims.getAuditId();
  }

  private CommandTrustMetadataDTO authorizeViaJwt(
      Header authHeader, ProcessInstanceTriggerEnvelope triggerEnvelope) {
    ProcessInstanceTriggerDTO trigger = triggerEnvelope.trigger();
    TokenClaims claims =
        triggerEnvelope.validatedJwtClaims() != null
            ? triggerEnvelope.validatedJwtClaims()
            : validateJwtClaims(authHeader, trigger);
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
   * <p>The deserializer has already verified the signature cryptographically. This method checks
   * envelope-level errors first, then delegates key resolution and trust checks to {@link
   * VerificationCore#verify(Header, KeyRole)}.
   */
  private CommandTrustMetadataDTO authorizeViaEd25519(
      Header sigHeader, ProcessInstanceTriggerEnvelope triggerEnvelope, KeyRole requiredRole) {
    if (triggerEnvelope.hasSignatureError()) {
      throw new AuthorizationTokenException(triggerEnvelope.signatureError());
    }

    // Delegate key resolution, revoke check, and trust-policy evaluation to VerificationCore.
    VerifiedMessageContext ctx = verificationCore.verify(sigHeader, requiredRole);

    // Check that the deserializer actually performed the cryptographic verification.
    if (!triggerEnvelope.signatureVerified()) {
      throw new AuthorizationTokenException(
          "Ed25519 header present for command "
              + triggerEnvelope.trigger().getClass().getSimpleName()
              + " but the signature was not verified by the deserializer");
    }

    boolean isEngine = keyTrustPolicy.isTrustedForRole(ctx.key(), KeyRole.ENGINE);
    log.info(
        "✅ Authorised (Ed25519) command={} keyId={} owner={} roleRequired={} derivedRole={}",
        triggerEnvelope.trigger().getClass().getSimpleName(),
        ctx.keyId(),
        ctx.key().getOwner(),
        requiredRole,
        ctx.role());

    return CommandTrustMetadataDTO.builder()
        .authMethod(CommandAuthMethod.ED25519)
        .verificationResult(
            isEngine
                ? CommandTrustVerificationResult.ENGINE_SIGNED
                : CommandTrustVerificationResult.SIGNATURE_VERIFIED)
        .trusted(true)
        .signerKeyId(ctx.keyId())
        .signerOwner(ctx.key().getOwner())
        .signerAlgorithm(ctx.key().getAlgorithm())
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


  private static boolean isSignatureGateActive(
      GlobalConfigurationDTO cfg, NamespaceSecurityPolicyDTO authoritativePolicy) {
    return cfg.isSigningEnabled() || isPolicyDrivenSignatureRequired(authoritativePolicy);
  }

  private static boolean isPolicyDrivenSignatureRequired(
      NamespaceSecurityPolicyDTO authoritativePolicy) {
    return isAnchoredPosture(authoritativePolicy);
  }

  private static boolean isEntryCommand(ProcessInstanceTriggerDTO trigger) {
    return trigger instanceof StartCommandDTO
        || trigger instanceof AbortTriggerDTO
        || trigger instanceof SetVariableTriggerDTO;
  }

  private static boolean isTaskCompletionTrigger(ProcessInstanceTriggerDTO trigger) {
    return trigger instanceof ExternalTaskResponseTriggerDTO
        || trigger instanceof UserTaskResponseTriggerDTO;
  }

  public static boolean isExternallyPublishedMessageEvent(Object value) {
    return value instanceof DefinitionMessageEventTriggerDTO
        || value instanceof CorrelationMessageEventTriggerDTO;
  }

  public static boolean isExternallyPublishedSignal(Object value) {
    return value != null && value.getClass() == io.taktx.dto.SignalDTO.class;
  }


  private void assertTrustAnchorRequirementSatisfied(
      NamespaceSecurityPolicyDTO authoritativePolicy) {
    if (!isAnchoredPosture(authoritativePolicy)) {
      return;
    }
    if (config.getPlatformPublicKey() == null || config.getPlatformPublicKey().isBlank()) {
      throw new AuthorizationTokenException(
          "Namespace security policy requires anchored trust but no platform public key is configured");
    }
  }

  private GlobalConfigurationDTO effectiveConfig() {
    if (globalConfigStore == null || globalConfigStore.get() == null) {
      return GlobalConfigurationDTO.builder().build();
    }
    return globalConfigStore.get();
  }

  private static boolean isAnchoredPosture(NamespaceSecurityPolicyDTO policy) {
    return policy != null && policy.getMode() == SecurityMode.ANCHORED;
  }

  private NamespaceSecurityPolicyDTO authoritativePolicy() {
    return namespaceSecurityPolicyStore != null
        ? namespaceSecurityPolicyStore.getAuthoritativePolicy()
        : null;
  }

  private static Header lastHeader(Headers headers, String headerName) {
    return headers != null ? headers.lastHeader(headerName) : null;
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
      case AbortTriggerDTO _ when !"CANCEL".equals(claims.getAction()) ->
          throw new AuthorizationTokenException(
              "Token action '" + claims.getAction() + "' does not match CANCEL command");
      case SetVariableTriggerDTO _ when !"SET_VARIABLE".equals(claims.getAction()) ->
          throw new AuthorizationTokenException(
              "Token action '" + claims.getAction() + "' does not match SET_VARIABLE command");
      case UserTaskResponseTriggerDTO _ when !"USER_TASK_COMPLETE".equals(claims.getAction()) ->
          throw new AuthorizationTokenException(
              "Token action '"
                  + claims.getAction()
                  + "' does not match USER_TASK_COMPLETE command");
      case ExternalTaskResponseTriggerDTO _ when !"EXTERNAL_TASK_COMPLETE"
              .equals(claims.getAction()) ->
          throw new AuthorizationTokenException(
              "Token action '"
                  + claims.getAction()
                  + "' does not match EXTERNAL_TASK_COMPLETE command");
      default ->
          log.debug(
              "No claim matching defined for {}, allowing", trigger.getClass().getSimpleName());
    }
  }
}
