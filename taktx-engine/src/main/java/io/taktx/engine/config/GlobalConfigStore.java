/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.config;

import io.taktx.dto.GlobalConfigurationDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CDI bean that holds the latest {@link GlobalConfigurationDTO} received from the {@code
 * taktx-configuration} compacted topic (key {@code "config"}).
 *
 * <p>Written by {@link io.taktx.engine.license.LicenseConfigProcessor} on the Kafka Streams
 * GlobalStreamThread; read by {@link io.taktx.engine.security.MessageSigningService} on any thread.
 * Uses an {@link AtomicReference} for safe, lock-free cross-thread access.
 */
@ApplicationScoped
public class GlobalConfigStore {

  private final AtomicReference<GlobalConfigurationDTO> config = new AtomicReference<>(null);

  /** Called by {@code LicenseConfigProcessor} whenever a {@code "config"} record arrives. */
  public void update(GlobalConfigurationDTO dto) {
    config.set(dto);
  }

  /** Returns the latest configuration, or {@code null} if no record has been received yet. */
  public GlobalConfigurationDTO get() {
    return config.get();
  }
}
