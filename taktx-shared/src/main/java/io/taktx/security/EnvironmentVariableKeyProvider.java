/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

/** Reads signing keys from environment variables or system properties. */
public class EnvironmentVariableKeyProvider implements SigningKeyProvider {
  private static final String PRIVATE_ENV_VAR = "TAKTX_SIGNING_PRIVATE_KEY";
  private static final String PRIVATE_SYS_PROP = "taktx.signing.private-key";
  private static final String PUBLIC_ENV_VAR = "TAKTX_SIGNING_PUBLIC_KEY";
  private static final String PUBLIC_SYS_PROP = "taktx.signing.public-key";

  @Override
  public String getPrivateKey(String keyId) {
    String key = System.getenv(PRIVATE_ENV_VAR);
    if (key != null && !key.isBlank()) return key;
    String prop = System.getProperty(PRIVATE_SYS_PROP);
    return (prop != null && !prop.isBlank()) ? prop : null;
  }

  @Override
  public String getPublicKey(String keyId) {
    String key = System.getenv(PUBLIC_ENV_VAR);
    if (key != null && !key.isBlank()) return key;
    String prop = System.getProperty(PUBLIC_SYS_PROP);
    return (prop != null && !prop.isBlank()) ? prop : null;
  }

  @Override
  public boolean hasKey(String keyId) {
    return getPrivateKey(keyId) != null;
  }
}
