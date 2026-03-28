/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.util.TaktPropertiesHelper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class TaktPropertiesHelperConfigurationTest {

  private TaktPropertiesHelperConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new TaktPropertiesHelperConfiguration();
  }

  private static StandardEnvironment environmentWith(Map<String, Object> properties) {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return env;
  }

  @Test
  void testTaktPropertiesHelperCreation() {
    // Given
    StandardEnvironment environment =
        environmentWith(
            Map.of(
                "taktx.engine.tenant-id", "test-tenant",
                "taktx.engine.namespace", "test-namespace",
                "kafka.bootstrap.servers", "localhost:9092"));

    // When
    TaktPropertiesHelper helper = configuration.taktPropertiesHelper(environment);

    // Then
    assertThat(helper).isNotNull();
    assertThat(helper.getTaktProperties()).isNotNull();
    assertThat(helper.getTaktProperties().getProperty("taktx.engine.tenant-id"))
        .isEqualTo("test-tenant");
    assertThat(helper.getTaktProperties().getProperty("taktx.engine.namespace"))
        .isEqualTo("test-namespace");
  }

  @Test
  void testTaktPropertiesHelperCreation_emptyEnvironment() {
    // Given – only the two mandatory fields; simulates a minimal config
    StandardEnvironment environment =
        environmentWith(
            Map.of(
                "taktx.engine.tenant-id", "test-tenant",
                "taktx.engine.namespace", "default"));

    // When
    TaktPropertiesHelper helper = configuration.taktPropertiesHelper(environment);

    // Then
    assertThat(helper).isNotNull();
    assertThat(helper.getTaktProperties()).isNotNull();
    assertThat(helper.getTaktProperties().getProperty("taktx.engine.tenant-id"))
        .isEqualTo("test-tenant");
    assertThat(helper.getTaktProperties().getProperty("taktx.engine.namespace"))
        .isEqualTo("default");
  }
}
