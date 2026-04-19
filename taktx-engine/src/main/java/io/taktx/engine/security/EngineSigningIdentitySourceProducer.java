/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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

  private static final String GENERATED_SOURCE = "generated";

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
    String sourceType =
        configuredSourceType == null ? GENERATED_SOURCE : configuredSourceType.trim();
    validateProductionMode(sourceType);
    if (sourceType.isEmpty() || GENERATED_SOURCE.equalsIgnoreCase(sourceType)) {
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

  private void validateProductionMode(String sourceType) {
    if (!config.isSecurityProductionMode()) {
      return;
    }
    if (sourceType.isEmpty() || GENERATED_SOURCE.equalsIgnoreCase(sourceType)) {
      throw new IllegalStateException(
          "taktx.security.production-mode=true requires taktx.signing.identity-source=file or env; "
              + "generated engine signing keys are not allowed in anchored production mode");
    }
    if (config.getEngineKeyRegistrationSignature() == null) {
      throw new IllegalStateException(
          "taktx.security.production-mode=true requires taktx.engine.key-registration-signature "
              + "(TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE) so the engine key is trusted in anchored mode");
    }
  }
}
