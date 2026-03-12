/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.engine.config.TaktConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DefaultLicenseManager} pushed-license state management.
 *
 * <p>The License3j signature verification path ({@link DefaultLicenseManager#parsePushedLicense})
 * is exercised in integration only (requires a real signed license binary). These unit tests cover
 * the in-memory state machine via {@link DefaultLicenseManager#updateFromLicensePush}.
 */
class DefaultLicenseManagerTest {

  private DefaultLicenseManager manager;

  @BeforeEach
  void setUp() {
    TaktConfiguration config = mock(TaktConfiguration.class);
    // Point at a non-existent file so init() sets licenseState=NOT_FOUND rather than failing.
    when(config.getLicenseFilePath()).thenReturn(Path.of("/non-existent/license.lic"));
    manager = new DefaultLicenseManager(config);
    manager.init();
  }

  @Test
  void beforePush_eventSigningNotAllowed() {
    assertThat(manager.isEventSigningAllowed()).isFalse();
  }

  @Test
  void beforePush_defaultMaxPartitionsIsFreeTier() {
    assertThat(manager.getMaxAllowedPartitions()).isEqualTo(3);
  }

  @Test
  void afterPush_maxPartitionsReflectsPushedValue() {
    manager.updateFromLicensePush("ENTERPRISE", 10, 50, true);
    assertThat(manager.getMaxAllowedPartitions()).isEqualTo(10);
  }

  @Test
  void afterPush_eventSigningReflectsPushedValue() {
    manager.updateFromLicensePush("ENTERPRISE", 10, 50, true);
    assertThat(manager.isEventSigningAllowed()).isTrue();
  }

  @Test
  void afterPush_eventSigningFalseWhenLicenseDoesNotPermit() {
    manager.updateFromLicensePush("COMMUNITY", 3, null, false);
    assertThat(manager.isEventSigningAllowed()).isFalse();
  }

  @Test
  void afterPush_unlimitedPartitions_returnsFreeTierDefault() {
    // null maxKafkaPartitions means unlimited; fall back to file-based value (3 since no file)
    manager.updateFromLicensePush("ENTERPRISE", null, null, true);
    assertThat(manager.getMaxAllowedPartitions()).isEqualTo(3);
  }

  @Test
  void secondPush_overridesFirstPush() {
    manager.updateFromLicensePush("STANDARD", 5, 20, false);
    manager.updateFromLicensePush("ENTERPRISE", 20, 100, true);

    assertThat(manager.getMaxAllowedPartitions()).isEqualTo(20);
    assertThat(manager.isEventSigningAllowed()).isTrue();
  }
}
