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
 *
 * <p>Default (no file, no push) is unlimited — TaktX is open source. SaaS deployments push a
 * license with a specific partition budget enforced.
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
  void beforePush_defaultPartitionBudgetIsUnlimited() {
    assertThat(manager.getPartitionBudget()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void beforePush_defaultLicenseStateIsNotFound() {
    assertThat(manager.getLicenseState()).isEqualTo(LicenseState.NOT_FOUND);
  }

  @Test
  void afterPush_partitionBudgetReflectsPushedValue() {
    manager.updateFromLicensePush("ENTERPRISE", 10);
    assertThat(manager.getPartitionBudget()).isEqualTo(10);
  }

  @Test
  void afterPush_licenseStateIsValid() {
    manager.updateFromLicensePush("STANDARD", 500);
    assertThat(manager.getLicenseState()).isEqualTo(LicenseState.VALID);
  }

  @Test
  void afterPush_unlimitedPartitions_returnsMaxValue() {
    manager.updateFromLicensePush("ENTERPRISE", Integer.MAX_VALUE);
    assertThat(manager.getPartitionBudget()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void afterPush_licenseInfoReflectsPushedType() {
    manager.updateFromLicensePush("ENTERPRISE", Integer.MAX_VALUE);
    assertThat(manager.getLicenseInfo()).contains("ENTERPRISE").contains("unlimited");
  }

  @Test
  void secondPush_overridesFirstPush() {
    manager.updateFromLicensePush("STANDARD", 180);
    manager.updateFromLicensePush("ENTERPRISE", 20);
    assertThat(manager.getPartitionBudget()).isEqualTo(20);
  }

  @Test
  void getPartitionBudget_returnsUnlimitedWhenNoPushAndNoLicense() {
    // No push, no license file → unlimited (open source default)
    assertThat(manager.getPartitionBudget()).isEqualTo(Integer.MAX_VALUE);
  }
}
