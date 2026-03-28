/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.taktx.engine.config.GlobalConfigStore;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests that {@link LicenseConfigProcessor} correctly intercepts records with key {@code "license"}
 * on the {@code taktx-configuration} topic and delegates to {@link
 * LicenseManager#parsePushedLicense(String)}.
 *
 * <p>Uses {@link TopologyTestDriver} — no broker required.
 */
class LicensePushTest {

  private static final String CONFIGURATION_TOPIC = "default.taktx-configuration";
  private static final String STORE_NAME = "license-processor-store";

  private TopologyTestDriver driver;
  private TestInputTopic<String, byte[]> configTopic;
  private LicenseManager licenseManager;

  @BeforeEach
  void setUp() {
    licenseManager = mock(LicenseManager.class);

    StreamsBuilder builder = new StreamsBuilder();
    builder.addGlobalStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.ByteArray())
            .withLoggingDisabled(),
        CONFIGURATION_TOPIC,
        Consumed.with(Serdes.String(), Serdes.ByteArray()),
        () -> new LicenseConfigProcessor(licenseManager, new GlobalConfigStore()));

    Topology topology = builder.build();

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "license-push-test");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass().getName());

    driver = new TopologyTestDriver(topology, config);
    configTopic =
        driver.createInputTopic(
            CONFIGURATION_TOPIC, Serdes.String().serializer(), Serdes.ByteArray().serializer());
  }

  @AfterEach
  void tearDown() {
    driver.close();
  }

  @Test
  void licenseKey_delegatesToLicenseManager() {
    String licenseText = "-----BEGIN LICENSE-----\nlicenseType=ENTERPRISE\n-----END LICENSE-----";
    configTopic.pipeInput("license", licenseText.getBytes(StandardCharsets.UTF_8));

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(licenseManager).parsePushedLicense(captor.capture());
    assertThat(captor.getValue()).isEqualTo(licenseText);
  }

  @Test
  void otherKey_doesNotDelegateToLicenseManager() {
    String configJson = "{\"signingEnabled\":false}";
    configTopic.pipeInput("config", configJson.getBytes(StandardCharsets.UTF_8));

    verifyNoInteractions(licenseManager);
  }

  @Test
  void tombstone_doesNotDelegateToLicenseManager() {
    configTopic.pipeInput("license", (byte[]) null);

    verifyNoInteractions(licenseManager);
  }

  @Test
  void multiplePublishes_eachDelegated() {
    String first = "-----BEGIN LICENSE-----\nlicenseType=COMMUNITY\n-----END LICENSE-----";
    String second = "-----BEGIN LICENSE-----\nlicenseType=ENTERPRISE\n-----END LICENSE-----";

    configTopic.pipeInput("license", first.getBytes(StandardCharsets.UTF_8));
    configTopic.pipeInput("license", second.getBytes(StandardCharsets.UTF_8));

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(licenseManager, times(2)).parsePushedLicense(captor.capture());
    assertThat(captor.getAllValues()).containsExactly(first, second);
  }
}
