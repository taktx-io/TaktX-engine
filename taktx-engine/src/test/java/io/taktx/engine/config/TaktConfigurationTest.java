/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.DmnValidationMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaktConfigurationTest {

  @Test
  void signingIdentitySourceType_defaultsToGeneratedWhenUnset() {
    TaktConfiguration config = new TaktConfiguration();
    config.sharedSigningIdentitySource = Optional.empty();

    assertThat(config.getSigningIdentitySourceType()).isEqualTo("generated");
  }

  @Test
  void signingIdentitySourceType_usesSharedValueWhenConfigured() {
    TaktConfiguration config = new TaktConfiguration();
    config.sharedSigningIdentitySource = Optional.of(" file ");

    assertThat(config.getSigningIdentitySourceType()).isEqualTo("file");
  }

  @Test
  void signingFilePaths_treatBlankValuesAsAbsent() {
    TaktConfiguration config = new TaktConfiguration();
    config.signingFileKeyIdPath = Optional.of("   ");
    config.signingFilePrivateKeyPath = Optional.empty();
    config.signingFilePublicKeyPath = Optional.of(" /tmp/public-key.b64 ");

    assertThat(config.getSigningFileKeyIdPath()).isNull();
    assertThat(config.getSigningFilePrivateKeyPath()).isNull();
    assertThat(config.getSigningFilePublicKeyPath()).isEqualTo("/tmp/public-key.b64");
  }

  @Test
  void signingFileRefreshInterval_exposesConfiguredValue() {
    TaktConfiguration config = new TaktConfiguration();
    config.signingFileRefreshIntervalMs = 2500L;

    assertThat(config.getSigningFileRefreshIntervalMs()).isEqualTo(2500L);
  }

  @Test
  void dmnValidationMode_defaultsToPermissiveWhenUnset() {
    assertThat(TaktConfiguration.parseDmnValidationMode(null))
        .isEqualTo(DmnValidationMode.PERMISSIVE);
    assertThat(TaktConfiguration.parseDmnValidationMode("   "))
        .isEqualTo(DmnValidationMode.PERMISSIVE);
  }

  @Test
  void dmnValidationMode_parsesConfiguredValues() {
    assertThat(TaktConfiguration.parseDmnValidationMode("warn")).isEqualTo(DmnValidationMode.WARN);
    assertThat(TaktConfiguration.parseDmnValidationMode(" STRICT "))
        .isEqualTo(DmnValidationMode.STRICT);
  }

  @Test
  void dmnValidationMode_rejectsUnknownValues() {
    assertThatThrownBy(() -> TaktConfiguration.parseDmnValidationMode("unsafe"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown DMN validation mode");
  }
}
