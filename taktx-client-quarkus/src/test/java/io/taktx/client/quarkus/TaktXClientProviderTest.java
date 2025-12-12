/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ResultProcessorFactory;
import io.taktx.client.TaktXClient;
import io.taktx.client.WorkerBeanInstanceProvider;
import jakarta.enterprise.event.Event;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaktXClientProviderTest {

  @Mock private Config config;

  @Mock private InstanceUpdateRecordObserverChecker observerChecker;

  @Mock private Event<InstanceUpdateRecord> events;

  @Mock private WorkerBeanInstanceProvider instanceProvider;

  @Mock private ParameterResolverFactory parameterResolverFactory;

  @Mock private ResultProcessorFactory resultProcessorFactory;

  private TaktXClientProvider provider;

  @BeforeEach
  void setUp() {
    // Reset static taktClient between tests
    try {
      java.lang.reflect.Field field = TaktXClientProvider.class.getDeclaredField("taktClient");
      field.setAccessible(true);
      field.set(null, null);
    } catch (Exception e) {
      // Ignore if field access fails
    }

    provider =
        new TaktXClientProvider(
            config,
            observerChecker,
            events,
            instanceProvider,
            parameterResolverFactory,
            resultProcessorFactory);

    // Set default values for ConfigProperty fields
    setPartitions(3);
    setReplicationFactor((short) 1);
    setGroupIdInstanceUpdate("test-group-id");
  }

  @Test
  void testInit_whenClientDisabled_shouldSkipInitialization() {
    // Given
    when(config.getOptionalValue("taktx.client.enabled", Boolean.class))
        .thenReturn(Optional.of(false));

    // When
    provider.init();

    // Then
    TaktXClient client = provider.taktClient();
    assertThat(client).isNull();
  }

  @Test
  void testTaktClient_returnsStoredInstance() {
    // Given - manually set a mock client
    TaktXClient mockClient = mock(TaktXClient.class);
    setStaticTaktClient(mockClient);

    // When
    TaktXClient result = provider.taktClient();

    // Then
    assertThat(result).isSameAs(mockClient);
  }

  @Test
  void testTaktClient_whenNotInitialized_returnsNull() {
    // Given - client not initialized

    // When
    TaktXClient result = provider.taktClient();

    // Then
    assertThat(result).isNull();
  }

  @Test
  void testConstructor_storesAllDependencies() {
    // Then - verify all dependencies are stored (implicitly by not throwing NPE)
    assertThat(provider).isNotNull();
  }

  // Helper methods to set private fields
  private void setPartitions(int partitions) {
    try {
      java.lang.reflect.Field field = TaktXClientProvider.class.getDeclaredField("partitions");
      field.setAccessible(true);
      field.set(provider, partitions);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set partitions field", e);
    }
  }

  private void setReplicationFactor(short replicationFactor) {
    try {
      java.lang.reflect.Field field =
          TaktXClientProvider.class.getDeclaredField("replicationFactor");
      field.setAccessible(true);
      field.set(provider, replicationFactor);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set replicationFactor field", e);
    }
  }

  private void setGroupIdInstanceUpdate(String groupId) {
    try {
      java.lang.reflect.Field field =
          TaktXClientProvider.class.getDeclaredField("groupIdInstanceUpdate");
      field.setAccessible(true);
      field.set(provider, groupId);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set groupIdInstanceUpdate field", e);
    }
  }

  private void setStaticTaktClient(TaktXClient client) {
    try {
      java.lang.reflect.Field field = TaktXClientProvider.class.getDeclaredField("taktClient");
      field.setAccessible(true);
      field.set(null, client);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set static taktClient field", e);
    }
  }
}
