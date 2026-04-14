/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.DmnValidationMode;
import io.taktx.dto.GlobalConfigurationDTO;
import org.junit.jupiter.api.Test;

class TaktXClientGlobalConfigPublishTest {

  @Test
  void buildConfigurationEvent_wrapsGlobalConfigurationUsingConfigUpdateType() {
    GlobalConfigurationDTO configuration =
        GlobalConfigurationDTO.builder()
            .signingEnabled(true)
            .engineRequiresAuthorization(true)
            .dmnValidationMode(DmnValidationMode.STRICT)
            .build();

    ConfigurationEventDTO event = TaktXClient.buildConfigurationEvent(configuration);

    assertThat(event.getEventType())
        .isEqualTo(ConfigurationEventDTO.ConfigurationEventType.CONFIGURATION_UPDATE);
    assertThat(event.getConfiguration()).isEqualTo(configuration);
    assertThat(event.getConfiguration().getDmnValidationMode()).isEqualTo(DmnValidationMode.STRICT);
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
