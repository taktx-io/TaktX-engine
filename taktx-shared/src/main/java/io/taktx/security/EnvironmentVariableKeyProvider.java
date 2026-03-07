/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

/** Reads the private signing key from TAKTX_SIGNING_PRIVATE_KEY env var or system property. */
public class EnvironmentVariableKeyProvider implements SigningKeyProvider {
  private static final String ENV_VAR = "TAKTX_SIGNING_PRIVATE_KEY";
  private static final String SYS_PROP = "taktx.signing.private-key";

  @Override
  public String getPrivateKey(String keyId) {
    String key = System.getenv(ENV_VAR);
    if (key != null && !key.isBlank()) return key;
    String prop = System.getProperty(SYS_PROP);
    return (prop != null && !prop.isBlank()) ? prop : null;
  }

  @Override
  public boolean hasKey(String keyId) {
    return getPrivateKey(keyId) != null;
  }
}
