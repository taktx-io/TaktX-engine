/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

class TaktPropertiesHelperConfigurationTest {

  private TaktPropertiesHelperConfiguration configuration;
  private ConfigurableEnvironment environment;

  @BeforeEach
  void setUp() {
    configuration = new TaktPropertiesHelperConfiguration();
    environment = mock(ConfigurableEnvironment.class);
  }

  @Test
  void testTaktPropertiesHelperCreation() {
    // Given
    MutablePropertySources propertySources = new MutablePropertySources();
    Properties props = new Properties();
    props.setProperty("taktx.namespace", "test-namespace");
    props.setProperty("kafka.bootstrap.servers", "localhost:9092");

    MapPropertySource propertySource = new MapPropertySource("test",
        props.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                e -> e.getKey().toString(),
                e -> e.getValue())));
    propertySources.addFirst(propertySource);

    org.mockito.Mockito.when(environment.getPropertySources()).thenReturn(propertySources);
    org.mockito.Mockito.when(environment.getProperty("taktx.namespace")).thenReturn("test-namespace");
    org.mockito.Mockito.when(environment.getProperty("kafka.bootstrap.servers")).thenReturn("localhost:9092");

    // When
    TaktPropertiesHelper helper = configuration.taktPropertiesHelper(environment);

    // Then
    assertThat(helper).isNotNull();
    assertThat(helper.getTaktProperties()).isNotNull();
  }

  @Test
  void testTaktPropertiesHelperCreation_emptyEnvironment() {
    // Given
    MutablePropertySources propertySources = new MutablePropertySources();
    org.mockito.Mockito.when(environment.getPropertySources()).thenReturn(propertySources);

    // When
    TaktPropertiesHelper helper = configuration.taktPropertiesHelper(environment);

    // Then
    assertThat(helper).isNotNull();
    assertThat(helper.getTaktProperties()).isNotNull();
  }
}

