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
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningException;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningIdentitySource;
import io.taktx.security.SigningKeyRegistrar;
import io.taktx.security.SigningServiceHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Signs engine-internal Kafka messages with Ed25519.
 *
 * <p>Registers itself as a {@link SigningServiceHolder.SigningFunction} at startup so that {@link
 * io.taktx.serdes.SigningSerializer} can sign records in a single serialisation pass — no
 * double-serialisation.
 */
@ApplicationScoped
@Startup
@Slf4j
public class MessageSigningService {

  private static final long PUBLICATION_RETRY_DELAY_SECONDS = 2L;

  private final TaktConfiguration config;
  private final GlobalConfigStore globalConfigStore;
  private final SigningIdentitySource signingIdentitySource;
  private final ScheduledExecutorService keyPublicationExecutor;

  private final AtomicBoolean publicKeyPublished = new AtomicBoolean(false);

  /** Cached at startup for self-verification and test access. */
  private String keyId;

  private String cachedPrivateKeyBase64;
  private String cachedPublicKeyBase64;

  /** Captured before a key rotation so the previous key can be retired to TRUSTED. */
  private volatile SigningIdentity previousIdentity;

  @Inject
  public MessageSigningService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      SigningIdentitySource signingIdentitySource) {
    this(config, globalConfigStore, signingIdentitySource, true);
  }

  /** Test constructor with a pre-built identity source and publication disabled. */
  MessageSigningService(
      TaktConfiguration config,
      GlobalConfigStore globalConfigStore,
      SigningIdentitySource signingIdentitySource,
      boolean startPublicationScheduler) {
    this.config = config;
    this.globalConfigStore = globalConfigStore;
    this.signingIdentitySource = signingIdentitySource;
    this.keyPublicationExecutor =
        startPublicationScheduler
            ? Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                  Thread thread = new Thread(runnable, "engine-signing-key-publisher");
                  thread.setDaemon(true);
                  return thread;
                })
            : null;
    if (!startPublicationScheduler) {
      refreshActiveIdentity();
      publicKeyPublished.set(true);
    }
  }

  @PostConstruct
  void registerSigningFunction() {
    refreshActiveIdentity();
    SigningServiceHolder.set(this::signToHeaderValue);
    log.debug("MessageSigningService registered in SigningServiceHolder");
    schedulePublicKeyPublication(0L);
  }

  /**
   * Publishes the engine's own Ed25519 public key to the {@code taktx-signing-keys} compacted topic
   * so that workers' {@link io.taktx.security.SigningKeysStore} can resolve it when verifying
   * instance-update and external-task-trigger signatures.
   *
   * <p>Without this, workers receive signed records whose {@code keyId} is unknown to their {@code
   * SigningKeysStore} → verification throws {@code IllegalStateException}.
   *
   * <p>The publish is best-effort: a failure is logged as a warning but does not prevent the engine
   * from starting. The compacted topic guarantees idempotency — re-publishing on every restart
   * simply overwrites the same key record.
   */
  private void schedulePublicKeyPublication(long delaySeconds) {
    if (keyPublicationExecutor == null || publicKeyPublished.get()) {
      return;
    }
    keyPublicationExecutor.schedule(this::publishEnginePublicKey, delaySeconds, TimeUnit.SECONDS);
  }

  private void publishEnginePublicKey() {
    SigningIdentity identity = refreshActiveIdentity();
    if (identity == null || publicKeyPublished.get()) {
      return;
    }
    try {
      String topic = config.getPrefixed(Topics.SIGNING_KEYS_TOPIC.getTopicName());
      SigningKeyRegistrar.publishPublicKey(
          config.getBootstrapServers(),
          topic,
          identity.getKeyId(),
          identity.getPublicKeyBase64(),
          "engine",
          identity.getAlgorithm(),
          KeyRole.ENGINE,
          config.getEngineKeyRegistrationSignature()); // null in community mode → omitted from DTO
      publicKeyPublished.set(true);
      log.info(
          "✅ Engine public key published to signing-keys topic: keyId={}", identity.getKeyId());
    } catch (Exception e) {
      log.warn(
          "Engine public key publication failed for keyId={} — retrying in {}s: {}",
          keyId,
          PUBLICATION_RETRY_DELAY_SECONDS,
          e.getMessage());
      schedulePublicKeyPublication(PUBLICATION_RETRY_DELAY_SECONDS);
    }
  }

  @PreDestroy
  void clearSigningFunction() {
    SigningServiceHolder.clear();
    if (keyPublicationExecutor != null) {
      keyPublicationExecutor.shutdownNow();
    }
  }

  /**
   * Returns the {@code X-TaktX-Signature} header value for the given payload bytes, or {@code null}
   * if signing is disabled or not configured. Called by {@link io.taktx.serdes.SigningSerializer}
   * via {@link SigningServiceHolder}.
   */
  public String signToHeaderValue(byte[] payloadBytes) {
    if (!effectiveConfig().isSigningEnabled()) {
      return null;
    }
    SigningIdentity identity = refreshActiveIdentity();
    if (identity == null) {
      log.debug(
          "No active signing identity available from source={}",
          signingIdentitySource.getSourceType());
      return null;
    }
    if (!publicKeyPublished.get()) {
      log.debug("Signing enabled but engine public key has not been published yet — skipping sign");
      return null;
    }
    try {
      byte[] signature = Ed25519Service.sign(payloadBytes, identity.getPrivateKeyBase64());

      // ── Self-verification (testing only) ──────────────────────────────────
      if (cachedPublicKeyBase64 != null) {
        try {
          // ↓ breakpoint here
          boolean selfCheckOk =
              Ed25519Service.verify(payloadBytes, signature, cachedPublicKeyBase64);
          log.debug(
              "Self-verify keyId={} ok={} publicKey={} payloadLen={}",
              keyId,
              selfCheckOk,
              cachedPublicKeyBase64,
              payloadBytes.length);
          if (!selfCheckOk) {
            log.error(
                "❌ Self-verify FAILED  keyId={} payloadLen={} — signature will be rejected by consumers",
                keyId,
                payloadBytes.length);
          }
        } catch (Exception selfCheckEx) {
          log.error(
              "❌ Self-verify ERROR  keyId={}: {}", keyId, selfCheckEx.getMessage(), selfCheckEx);
        }
      }
      // ─────────────────────────────────────────────────────────────────────

      return identity.toHeaderValue(signature);
    } catch (SigningException e) {
      log.error("Failed to sign message: {}", e.getMessage(), e);
      return null;
    }
  }

  public String getKeyId() {
    SigningIdentity identity = refreshActiveIdentity();
    return identity != null ? identity.getKeyId() : null;
  }

  public String getPublicKeyBase64() {
    refreshActiveIdentity();
    return cachedPublicKeyBase64;
  }

  private SigningIdentity refreshActiveIdentity() {
    SigningIdentity identity = signingIdentitySource.currentIdentity();
    if (identity == null) {
      return null;
    }
    boolean changed = !identity.getKeyId().equals(keyId);
    if (!changed) {
      changed =
          cachedPublicKeyBase64 == null
              || !cachedPublicKeyBase64.equals(identity.getPublicKeyBase64())
              || cachedPrivateKeyBase64 == null
              || !cachedPrivateKeyBase64.equals(identity.getPrivateKeyBase64());
    }
    if (changed) {
      // Capture previous identity before overwriting so we can retire it
      if (this.keyId != null
          && !this.keyId.equals(identity.getKeyId())
          && cachedPublicKeyBase64 != null) {
        previousIdentity =
            SigningIdentity.ed25519(this.keyId, cachedPrivateKeyBase64, cachedPublicKeyBase64);
      }
      this.keyId = identity.getKeyId();
      this.cachedPrivateKeyBase64 = identity.getPrivateKeyBase64();
      this.cachedPublicKeyBase64 = identity.getPublicKeyBase64();
      publicKeyPublished.set(!identity.hasPublicKey());
      log.info(
          "Active engine signing identity loaded from source={} keyId={}",
          signingIdentitySource.getSourceType(),
          keyId);
      if (identity.hasPublicKey() && keyPublicationExecutor != null) {
        schedulePublicKeyPublication(0L);
        // Retire the previous key if one existed
        if (previousIdentity != null) {
          retirePreviousKey();
        }
      }
    }
    return identity;
  }

  /**
   * Publishes the previous engine key with {@code status=TRUSTED} so it is still accepted for
   * in-flight verification but no longer the active signing key.
   *
   * <p>Full revocation (TRUSTED → REVOKED) is left to operational tooling or a future scheduled
   * task, since the drain window depends on consumer lag.
   */
  private void retirePreviousKey() {
    SigningIdentity prev = previousIdentity;
    if (prev == null || keyPublicationExecutor == null) return;
    keyPublicationExecutor.schedule(
        () -> {
          try {
            String topic = config.getPrefixed(Topics.SIGNING_KEYS_TOPIC.getTopicName());
            SigningKeyDTO trustedKey =
                SigningKeyDTO.builder()
                    .keyId(prev.getKeyId())
                    .publicKeyBase64(prev.getPublicKeyBase64())
                    .algorithm(prev.getAlgorithm())
                    .status(KeyStatus.TRUSTED)
                    .owner("engine")
                    .role(KeyRole.ENGINE)
                    .build();
            SigningKeyRegistrar.publishKeyWithStatus(
                config.getBootstrapServers(), topic, trustedKey);
            log.info("Previous engine key retired to TRUSTED: keyId={}", prev.getKeyId());
          } catch (Exception e) {
            log.warn(
                "Failed to retire previous engine key keyId={}: {}",
                prev.getKeyId(),
                e.getMessage());
          }
        },
        PUBLICATION_RETRY_DELAY_SECONDS,
        TimeUnit.SECONDS);
  }

  private GlobalConfigurationDTO effectiveConfig() {
    if (globalConfigStore == null || globalConfigStore.get() == null) {
      return GlobalConfigurationDTO.builder().build();
    }
    return globalConfigStore.get();
  }
}
