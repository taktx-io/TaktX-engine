/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taktx.util.TaktPropertiesHelper;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaktPropertiesHelperProducerTest {

  @Mock private Config config;

  private TaktPropertiesHelperProducer producer;

  @BeforeEach
  void setUp() {
    producer = new TaktPropertiesHelperProducer(config);
  }

  /** Returns the minimum set of property names required by TaktPropertiesHelper. */
  private static Iterable<String> requiredNames() {
    return java.util.Arrays.asList("taktx.engine.tenant-id", "taktx.engine.namespace");
  }

  /** Stubs the two mandatory properties on the mock config. */
  private void stubRequiredProperties() {
    when(config.getOptionalValue("taktx.engine.tenant-id", String.class))
        .thenReturn(Optional.of("test-tenant"));
    when(config.getOptionalValue("taktx.engine.namespace", String.class))
        .thenReturn(Optional.of("default"));
  }

  @Test
  void testTaktPropertiesHelperProduction_withEmptyConfig() {
    // Given – only the mandatory properties are present
    when(config.getPropertyNames()).thenReturn(requiredNames());
    stubRequiredProperties();

    // When
    TaktPropertiesHelper helper = producer.taktPropertiesHelper();

    // Then
    assertThat(helper).isNotNull();
    assertThat(helper).isInstanceOf(TaktPropertiesHelper.class);
  }

  @Test
  void testTaktPropertiesHelperProduction_withProperties() {
    // Given
    Iterable<String> propertyNames =
        java.util.Arrays.asList(
            "taktx.engine.tenant-id",
            "taktx.engine.namespace",
            "taktx.property1",
            "taktx.property2",
            "other.property");
    when(config.getPropertyNames()).thenReturn(propertyNames);
    stubRequiredProperties();
    when(config.getOptionalValue("taktx.property1", String.class))
        .thenReturn(Optional.of("value1"));
    when(config.getOptionalValue("taktx.property2", String.class))
        .thenReturn(Optional.of("value2"));
    when(config.getOptionalValue("other.property", String.class))
        .thenReturn(Optional.of("otherValue"));

    // When
    TaktPropertiesHelper helper = producer.taktPropertiesHelper();

    // Then
    assertThat(helper).isNotNull();
  }

  @Test
  void testTaktPropertiesHelperProduction_withEmptyOptionalValues() {
    // Given – property list contains required keys; their values are stubbed explicitly
    Iterable<String> propertyNames =
        java.util.Arrays.asList(
            "taktx.engine.tenant-id", "taktx.engine.namespace", "property1", "property2");
    when(config.getPropertyNames()).thenReturn(propertyNames);
    stubRequiredProperties();
    when(config.getOptionalValue("property1", String.class)).thenReturn(Optional.empty());
    when(config.getOptionalValue("property2", String.class)).thenReturn(Optional.empty());

    // When
    TaktPropertiesHelper helper = producer.taktPropertiesHelper();

    // Then
    assertThat(helper).isNotNull();
  }

  @Test
  void testTaktPropertiesHelperProduction_returnsNewInstance() {
    // Given
    when(config.getPropertyNames()).thenReturn(requiredNames());
    stubRequiredProperties();

    // When
    TaktPropertiesHelper helper1 = producer.taktPropertiesHelper();
    TaktPropertiesHelper helper2 = producer.taktPropertiesHelper();

    // Then
    assertThat(helper1).isNotNull();
    assertThat(helper2).isNotNull();
    // Each call creates a new instance
    assertThat(helper1).isNotSameAs(helper2);
  }

  @Test
  void testTaktPropertiesHelperProduction_handlesMultiplePropertiesCorrectly() {
    // Given
    Iterable<String> propertyNames =
        java.util.Arrays.asList(
            "taktx.engine.tenant-id",
            "taktx.engine.namespace",
            "taktx.kafka.bootstrap.servers",
            "taktx.engine.topic.partitions",
            "taktx.client.groupId");
    when(config.getPropertyNames()).thenReturn(propertyNames);
    stubRequiredProperties();
    when(config.getOptionalValue("taktx.kafka.bootstrap.servers", String.class))
        .thenReturn(Optional.of("localhost:9092"));
    when(config.getOptionalValue("taktx.engine.topic.partitions", String.class))
        .thenReturn(Optional.of("3"));
    when(config.getOptionalValue("taktx.client.groupId", String.class))
        .thenReturn(Optional.of("test-group"));

    // When
    TaktPropertiesHelper helper = producer.taktPropertiesHelper();

    // Then
    assertThat(helper).isNotNull();
  }
}
