/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.engine.config.GlobalConfigStore;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global-store processor that replaces the previous {@code globalTable(CONFIGURATION_TOPIC, ...)}
 * registration. It handles the single {@code taktx-configuration} topic for both record keys:
 *
 * <ul>
 *   <li>{@code "config"} — deserialises {@link ConfigurationEventDTO} (JSON) and updates the shared
 *       {@link GlobalConfigStore} so {@code MessageSigningService} can read it.
 *   <li>{@code "license"} — forwards the raw UTF-8 License3j text to {@link
 *       LicenseManager#parsePushedLicense(String)}.
 * </ul>
 *
 * <p>Runs on the Kafka Streams {@code GlobalStreamThread} — the natural, topology-native way to
 * react to changes without polling or scheduling. No downstream output is produced; the backing
 * store exists only to satisfy the Kafka Streams {@code addGlobalStore} API contract.
 */
public class LicenseConfigProcessor implements Processor<String, byte[], Void, Void> {

  private static final Logger log = LoggerFactory.getLogger(LicenseConfigProcessor.class);

  static final String LICENSE_KEY = "license";
  static final String CONFIG_KEY = "config";

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .configure(
              com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

  private final LicenseManager licenseManager;
  private final GlobalConfigStore globalConfigStore;

  public LicenseConfigProcessor(
      LicenseManager licenseManager, GlobalConfigStore globalConfigStore) {
    this.licenseManager = licenseManager;
    this.globalConfigStore = globalConfigStore;
  }

  @Override
  public void init(ProcessorContext<Void, Void> context) {
    // nothing to initialise
  }

  @Override
  public void process(Record<String, byte[]> rec) {
    if (rec.value() == null) {
      log.debug("Tombstone received for key='{}' on configuration topic — ignoring", rec.key());
      return;
    }

    if (LICENSE_KEY.equals(rec.key())) {
      String licenseText = new String(rec.value(), StandardCharsets.UTF_8);
      log.info("Received license push via configuration topic — parsing and verifying");
      licenseManager.parsePushedLicense(licenseText);

    } else if (CONFIG_KEY.equals(rec.key())) {
      try {
        ConfigurationEventDTO event =
            OBJECT_MAPPER.readValue(rec.value(), ConfigurationEventDTO.class);
        if (event != null && event.getConfiguration() != null) {
          globalConfigStore.update(event.getConfiguration());
          log.debug("Global configuration updated from taktx-configuration topic");
        }
      } catch (Exception e) {
        log.warn(
            "Failed to deserialise ConfigurationEventDTO from configuration topic: {}",
            e.getMessage());
      }
    }
    // All other keys are silently ignored.
  }

  @Override
  public void close() {
    // nothing to close
  }
}
