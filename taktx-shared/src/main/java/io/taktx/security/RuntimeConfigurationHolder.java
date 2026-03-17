/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import io.taktx.dto.GlobalConfigurationDTO;

/**
 * Process-wide holder for the latest runtime {@link GlobalConfigurationDTO} seen by a client.
 *
 * <p>This exists for the same reason as {@link SigningKeysStoreHolder}: Kafka deserializers are
 * instantiated reflectively, so they cannot receive runtime config via constructor injection. The
 * client updates this holder from the {@code taktx-configuration} topic and deserializers consult
 * it on every record, allowing flags such as {@code signingEnabled} to change without recreating
 * consumers.
 */
public final class RuntimeConfigurationHolder {

  private static final GlobalConfigurationDTO DEFAULT_CONFIG =
      GlobalConfigurationDTO.builder().build();

  private static volatile GlobalConfigurationDTO instance = DEFAULT_CONFIG;

  private RuntimeConfigurationHolder() {}

  public static void set(GlobalConfigurationDTO config) {
    instance = config != null ? config : DEFAULT_CONFIG;
  }

  public static GlobalConfigurationDTO get() {
    return instance != null ? instance : DEFAULT_CONFIG;
  }

  public static boolean isSigningEnabled() {
    return get().isSigningEnabled();
  }

  public static boolean isAuthorizationEnabled() {
    return get().isAuthorizationEnabled();
  }

  public static void clear() {
    instance = DEFAULT_CONFIG;
  }
}
