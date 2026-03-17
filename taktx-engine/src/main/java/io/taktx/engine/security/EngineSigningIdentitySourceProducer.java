/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.EnvironmentWorkerSigningIdentitySource;
import io.taktx.security.FileSigningIdentitySource;
import io.taktx.security.SigningIdentitySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/** Selects the engine signing identity source from configuration. */
@ApplicationScoped
public class EngineSigningIdentitySourceProducer {

  private final TaktConfiguration config;

  @Inject
  public EngineSigningIdentitySourceProducer(TaktConfiguration config) {
    this.config = config;
  }

  @Produces
  @ApplicationScoped
  public SigningIdentitySource signingIdentitySource() {
    return create(config.getSigningIdentitySourceType());
  }

  SigningIdentitySource create(String configuredSourceType) {
    String sourceType = configuredSourceType == null ? "generated" : configuredSourceType.trim();
    if (sourceType.isEmpty() || "generated".equalsIgnoreCase(sourceType)) {
      return new GeneratedEngineSigningIdentitySource();
    }
    if ("env".equalsIgnoreCase(sourceType) || "environment".equalsIgnoreCase(sourceType)) {
      return new EnvironmentWorkerSigningIdentitySource();
    }
    if ("file".equalsIgnoreCase(sourceType)) {
      return new FileSigningIdentitySource(
          config.getSigningFileKeyIdPath(),
          config.getSigningFilePrivateKeyPath(),
          config.getSigningFilePublicKeyPath(),
          config.getSigningFileRefreshIntervalMs());
    }
    throw new IllegalArgumentException(
        "Unsupported taktx.signing.identity-source='"
            + configuredSourceType
            + "'. Supported values: generated, env, file");
  }
}
