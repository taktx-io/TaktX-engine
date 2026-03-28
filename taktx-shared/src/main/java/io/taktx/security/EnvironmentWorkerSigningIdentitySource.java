/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import java.util.Map;
import java.util.Properties;

/** Reads a worker signing identity from environment variables or system properties. */
public class EnvironmentWorkerSigningIdentitySource implements SigningIdentitySource {

  private static final String PRIVATE_ENV_VAR = "TAKTX_SIGNING_PRIVATE_KEY";
  private static final String PRIVATE_SYS_PROP = "taktx.signing.private-key";
  private static final String PUBLIC_ENV_VAR = "TAKTX_SIGNING_PUBLIC_KEY";
  private static final String PUBLIC_SYS_PROP = "taktx.signing.public-key";
  private static final String KEY_ID_ENV_VAR = "TAKTX_SIGNING_KEY_ID";
  private static final String KEY_ID_SYS_PROP = "taktx.signing.key-id";

  private final Map<String, String> environment;
  private final Properties systemProperties;
  private final String keyIdOverride;

  public EnvironmentWorkerSigningIdentitySource() {
    this(System.getenv(), System.getProperties(), null);
  }

  public EnvironmentWorkerSigningIdentitySource(String keyIdOverride) {
    this(System.getenv(), System.getProperties(), keyIdOverride);
  }

  public EnvironmentWorkerSigningIdentitySource(Properties systemProperties, String keyIdOverride) {
    this(System.getenv(), systemProperties, keyIdOverride);
  }

  EnvironmentWorkerSigningIdentitySource(
      Map<String, String> environment, Properties systemProperties, String keyIdOverride) {
    this.environment = environment;
    this.systemProperties = systemProperties;
    this.keyIdOverride = keyIdOverride;
  }

  @Override
  public SigningIdentity currentIdentity() {
    String privateKey =
        firstNonBlank(
            environment.get(PRIVATE_ENV_VAR), systemProperties.getProperty(PRIVATE_SYS_PROP));
    if (privateKey == null) {
      return null;
    }

    String publicKey =
        firstNonBlank(
            environment.get(PUBLIC_ENV_VAR), systemProperties.getProperty(PUBLIC_SYS_PROP));
    String keyId =
        firstNonBlank(
            keyIdOverride,
            environment.get(KEY_ID_ENV_VAR),
            systemProperties.getProperty(KEY_ID_SYS_PROP));
    if (keyId == null) {
      throw new IllegalArgumentException(
          "TAKTX_SIGNING_PRIVATE_KEY is set but TAKTX_SIGNING_KEY_ID is missing. Both must be provided together.");
    }
    return SigningIdentity.ed25519(keyId, privateKey, publicKey);
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }
}
