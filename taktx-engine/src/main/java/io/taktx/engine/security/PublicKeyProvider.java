/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.taktx.engine.config.TaktConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds the Platform Service RSA public key — the root of trust for command authorization.
 *
 * <p>The key is set once at startup from {@code TAKTX_PLATFORM_PUBLIC_KEY} env var. Key rotation
 * (Phase 2) will be handled via the config topic.
 */
@ApplicationScoped
@Slf4j
public class PublicKeyProvider {

  private final TaktConfiguration config;
  private volatile PublicKey platformKey;

  @Inject
  public PublicKeyProvider(TaktConfiguration config) {
    this.config = config;
  }

  @PostConstruct
  void init() {
    if (!config.isAuthorizationEnabled()) {
      log.info("Command authorization is disabled — PublicKeyProvider inactive");
      return;
    }

    String base64Key =
        config
            .getPlatformPublicKeyBase64()
            .filter(s -> !s.isBlank())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "TAKTX_PLATFORM_PUBLIC_KEY must be set when"
                            + " taktx.security.authorization.enabled=true"));

    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64Key);
      platformKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
      log.info("✅ Root of trust established from TAKTX_PLATFORM_PUBLIC_KEY");
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse TAKTX_PLATFORM_PUBLIC_KEY: " + e.getMessage(), e);
    }
  }

  public PublicKey getPlatformKey() {
    return platformKey;
  }

  public boolean isReady() {
    return platformKey != null;
  }

  /** Stub for Phase 2 key rotation. */
  public void rotateKey(String newBase64) {
    log.warn("Key rotation requested but not yet implemented (Phase 2)");
  }
}
