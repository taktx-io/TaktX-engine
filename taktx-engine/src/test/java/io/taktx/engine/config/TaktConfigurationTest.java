/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

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
}
