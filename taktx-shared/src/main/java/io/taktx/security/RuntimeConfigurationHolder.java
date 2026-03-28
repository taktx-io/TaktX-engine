/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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

  public static boolean isEngineRequiresAuthorization() {
    return get().isEngineRequiresAuthorization();
  }

  public static void clear() {
    instance = DEFAULT_CONFIG;
  }
}
