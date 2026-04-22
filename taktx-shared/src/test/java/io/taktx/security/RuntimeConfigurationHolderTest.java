/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.ReplayProtectionMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeConfigurationHolderTest {

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
  }

  @Test
  void defaultsExposeCompatibilityReplaySettings() {
    RuntimeConfigurationHolder.clear();

    assertThat(RuntimeConfigurationHolder.isSigningEnabled()).isFalse();
    assertThat(RuntimeConfigurationHolder.isEngineRequiresAuthorization()).isFalse();
    assertThat(RuntimeConfigurationHolder.getReplayProtectionMode())
        .isEqualTo(ReplayProtectionMode.COMPAT);
    assertThat(RuntimeConfigurationHolder.getReplayProtectionRetentionMs()).isEqualTo(600_000L);
  }

  @Test
  void setExposesConfiguredReplaySettings() {
    RuntimeConfigurationHolder.set(
        GlobalConfigurationDTO.builder()
            .signingEnabled(true)
            .engineRequiresAuthorization(true)
            .replayProtectionMode(ReplayProtectionMode.STRICT)
            .replayProtectionRetentionMs(123_456L)
            .build());

    assertThat(RuntimeConfigurationHolder.isSigningEnabled()).isTrue();
    assertThat(RuntimeConfigurationHolder.isEngineRequiresAuthorization()).isTrue();
    assertThat(RuntimeConfigurationHolder.getReplayProtectionMode())
        .isEqualTo(ReplayProtectionMode.STRICT);
    assertThat(RuntimeConfigurationHolder.getReplayProtectionRetentionMs()).isEqualTo(123_456L);
  }
}
