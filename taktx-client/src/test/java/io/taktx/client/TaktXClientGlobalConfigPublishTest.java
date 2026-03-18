/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import org.junit.jupiter.api.Test;

class TaktXClientGlobalConfigPublishTest {

  @Test
  void buildConfigurationEvent_wrapsGlobalConfigurationUsingConfigUpdateType() {
    GlobalConfigurationDTO configuration =
        GlobalConfigurationDTO.builder().signingEnabled(true).authorizationEnabled(true).build();

    ConfigurationEventDTO event = TaktXClient.buildConfigurationEvent(configuration);

    assertThat(event.getEventType())
        .isEqualTo(ConfigurationEventDTO.ConfigurationEventType.CONFIGURATION_UPDATE);
    assertThat(event.getConfiguration()).isEqualTo(configuration);
    assertThat(event.getTimestamp()).isNotNull();
    assertThat(TaktXClient.CONFIGURATION_RECORD_KEY).isEqualTo("config");
  }

  @Test
  void publishGlobalConfig_rejectsNullConfiguration() {
    assertThatThrownBy(() -> TaktXClient.publishGlobalConfig(new java.util.Properties(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configuration must not be null");
  }
}
