/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.taktx.Topics;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.license.LicenseManager;
import io.taktx.security.EnvironmentVariableKeyProvider;
import io.taktx.security.SigningKeyProvider;
import io.taktx.security.SigningKeyRegistrar;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Publishes the engine Ed25519 public key to taktx-signing-keys at startup. Workers and the
 * ingester discover the engine's key from this compacted topic.
 */
@ApplicationScoped
@Slf4j
public class EngineSigningKeyPublisher {
  private final TaktConfiguration config;
  private final String bootstrapServers;
  private final LicenseManager licenseManager;

  @Inject
  public EngineSigningKeyPublisher(
      TaktConfiguration config,
      @ConfigProperty(name = "kafka.bootstrap.servers") String bootstrapServers,
      LicenseManager licenseManager) {
    this.config = config;
    this.bootstrapServers = bootstrapServers;
    this.licenseManager = licenseManager;
  }

  /** Called during startup, before Kafka Streams begins processing. */
  public void publishIfEnabled() {
    if (!config.isSigningEnabled()) {
      log.debug("Engine signing disabled — skipping key publication");
      return;
    }
    if (!licenseManager.isEventSigningAllowed()) {
      log.warn(
          "taktx.security.signing.enabled=true but the active license does not permit event"
              + " signing — key publication skipped");
      return;
    }
    String keyId = System.getenv("TAKTX_SIGNING_KEY_ID");
    if (keyId == null || keyId.isBlank()) keyId = System.getProperty("taktx.signing.key-id");
    if (keyId == null || keyId.isBlank()) {
      log.warn("taktx.security.signing.enabled=true but TAKTX_SIGNING_KEY_ID not set");
      return;
    }
    SigningKeyProvider keyProvider = new EnvironmentVariableKeyProvider();
    if (!keyProvider.hasKey(keyId)) {
      log.warn("TAKTX_SIGNING_KEY_ID={} set but private key env var missing", keyId);
      return;
    }
    try {
      String publicKeyBase64 = keyProvider.getPublicKey(keyId);
      if (publicKeyBase64 == null) {
        log.warn(
            "TAKTX_SIGNING_KEY_ID={} set but public key env var missing"
                + " (set TAKTX_SIGNING_PUBLIC_KEY or taktx.signing.public-key)",
            keyId);
        return;
      }
      String topic = config.getPrefixed(Topics.SIGNING_KEYS_TOPIC.getTopicName());
      SigningKeyRegistrar.publishPublicKey(
          bootstrapServers, topic, keyId, publicKeyBase64, "engine");
    } catch (Exception e) {
      log.error("Failed to publish engine signing key at startup: {}", e.getMessage(), e);
    }
  }
}
