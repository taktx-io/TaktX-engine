/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.quarkus.runtime.Startup;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EnvironmentVariableKeyProvider;
import io.taktx.security.SigningException;
import io.taktx.security.SigningKeyProvider;
import io.taktx.security.SigningKeyRegistrar;
import io.taktx.security.SigningServiceHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

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

  private static final String CONFIG_KEY = "config";

  private final TaktConfiguration config;
  private final SigningKeyProvider keyProvider;
  private final KafkaStreams kafkaStreams;

  private ReadOnlyKeyValueStore<String, ConfigurationEventDTO> configStore;

  /** Cached at startup for self-verification. */
  private String cachedPublicKeyBase64;

  @Inject
  public MessageSigningService(TaktConfiguration config, KafkaStreams kafkaStreams) {
    this.config = config;
    this.kafkaStreams = kafkaStreams;
    this.keyProvider = new EnvironmentVariableKeyProvider();
  }

  MessageSigningService(
      TaktConfiguration config, KafkaStreams kafkaStreams, SigningKeyProvider keyProvider) {
    this.config = config;
    this.kafkaStreams = kafkaStreams;
    this.keyProvider = keyProvider;
  }

  @PostConstruct
  void registerSigningFunction() {
    SigningServiceHolder.set(this::signToHeaderValue);
    log.debug("MessageSigningService registered in SigningServiceHolder");
    publishEnginePublicKey();
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
  private void publishEnginePublicKey() {
    if (!config.isSigningEnabled()) return;
    String keyId = config.getSigningKeyId().filter(s -> !s.isBlank()).orElse(null);
    if (keyId == null) {
      log.debug("No signing keyId configured — skipping engine public key publication");
      return;
    }
    String privateKey = keyProvider.getPrivateKey(keyId);
    if (privateKey == null) {
      log.warn("Cannot publish engine public key — no private key available for keyId={}", keyId);
      return;
    }
    try {
      String publicKeyBase64 = keyProvider.getPublicKey(keyId);
      if (publicKeyBase64 == null) {
        log.warn(
            "Cannot publish engine public key — no public key available for keyId={} "
                + "(set TAKTX_SIGNING_PUBLIC_KEY or taktx.signing.public-key)",
            keyId);
        return;
      }
      String topic = config.getPrefixed(io.taktx.Topics.SIGNING_KEYS_TOPIC.getTopicName());
      SigningKeyRegistrar.publishPublicKey(
          config.getBootstrapServers(), topic, keyId, publicKeyBase64, "engine");
      cachedPublicKeyBase64 = publicKeyBase64;
      log.info("✅ Engine public key published to signing-keys topic: keyId={}", keyId);
    } catch (Exception e) {
      log.warn(
          "Failed to publish engine public key (workers may not verify signatures): {}",
          e.getMessage());
    }
  }

  @PreDestroy
  void clearSigningFunction() {
    SigningServiceHolder.clear();
  }

  /**
   * Returns the {@code X-TaktX-Signature} header value for the given payload bytes, or {@code null}
   * if signing is disabled or not configured. Called by {@link io.taktx.serdes.SigningSerializer}
   * via {@link SigningServiceHolder}.
   */
  public String signToHeaderValue(byte[] payloadBytes) {
    if (!config.isSigningEnabled()) return null;

    String keyId = resolveKeyId();
    if (keyId == null) {
      log.warn("Signing is enabled but no signingKeyId configured (env or global config)");
      return null;
    }
    if (!keyProvider.hasKey(keyId)) {
      log.error("Signing enabled but no private key available for signingKeyId={}", keyId);
      return null;
    }
    try {
      String privateKey = keyProvider.getPrivateKey(keyId);
      byte[] signature = Ed25519Service.sign(payloadBytes, privateKey);

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

      return keyId + "." + Base64.getEncoder().encodeToString(signature);
    } catch (SigningException e) {
      log.error("Failed to sign message: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * Returns the signing keyId to use. Precedence:
   *
   * <ol>
   *   <li>Global config KTable (runtime reconfiguration via console)
   *   <li>{@code TAKTX_SIGNING_KEY_ID} env var (startup configuration)
   * </ol>
   */
  private String resolveKeyId() {
    GlobalConfigurationDTO globalConfig = getGlobalConfig();
    if (globalConfig != null && globalConfig.isSigningEnabled()) {
      String kid = globalConfig.getSigningKeyId();
      if (kid != null && !kid.isBlank()) return kid;
    }
    // Fall back to env/application.properties
    return config.getSigningKeyId().filter(s -> !s.isBlank()).orElse(null);
  }

  private GlobalConfigurationDTO getGlobalConfig() {
    try {
      if (configStore == null) {
        configStore =
            kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                    config.getPrefixed(Stores.GLOBAL_CONFIGURATION.getStorename()),
                    QueryableStoreTypes.keyValueStore()));
      }
      ConfigurationEventDTO event = configStore.get(CONFIG_KEY);
      return event != null ? event.getConfiguration() : null;
    } catch (Exception e) {
      log.debug("Could not read global configuration store: {}", e.getMessage());
      return null;
    }
  }
}
