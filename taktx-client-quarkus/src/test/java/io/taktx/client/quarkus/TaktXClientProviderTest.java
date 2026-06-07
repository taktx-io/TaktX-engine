/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.client.AnnotationScanningExternalTaskTriggerConsumer;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.InstanceUpdateStartStrategy;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ResultProcessorFactory;
import io.taktx.client.TaktXClient;
import io.taktx.client.TaktXClient.TaktXClientBuilder;
import io.taktx.client.WorkerBeanInstanceProvider;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.SecurityParticipantDescriptor;
import jakarta.enterprise.event.Event;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaktXClientProviderTest {

  @Mock private Config config;

  @Mock private InstanceUpdateRecordObserverChecker observerChecker;

  @Mock private Event<InstanceUpdateRecord> events;

  @Mock private WorkerBeanInstanceProvider instanceProvider;

  @Mock private ParameterResolverFactory parameterResolverFactory;

  @Mock private ResultProcessorFactory resultProcessorFactory;

  private TestableTaktXClientProvider provider;
  private TaktXClientBuilder builder;
  private TaktXClient client;
  private AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer;

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

    builder = mock(TaktXClientBuilder.class);
    client = mock(TaktXClient.class);
    externalTaskTriggerConsumer = mock(AnnotationScanningExternalTaskTriggerConsumer.class);

    when(builder.withParticipantDescriptor(any(SecurityParticipantDescriptor.class)))
        .thenReturn(builder);
    when(builder.withTaktParameterResolverFactory(parameterResolverFactory)).thenReturn(builder);
    when(builder.withResultProcessorFactory(resultProcessorFactory)).thenReturn(builder);
    when(builder.withProperties(any(Properties.class))).thenReturn(builder);
    when(builder.build()).thenReturn(client);
    when(externalTaskTriggerConsumer.getJobIds()).thenReturn(Collections.emptySet());

    provider =
        new TestableTaktXClientProvider(
            config,
            observerChecker,
            events,
            instanceProvider,
            parameterResolverFactory,
            resultProcessorFactory,
            builder,
            externalTaskTriggerConsumer);

    // Set default values for ConfigProperty fields
    setPartitions(3);
    setReplicationFactor((short) 1);
    setGroupIdInstanceUpdate("test-group-id");
    setInstanceUpdateStartStrategy("RESUME");
    provider.builtProperties = defaultProperties();
  }

  @Test
  void testInit_whenClientDisabled_shouldSkipInitialization() {
    when(config.getOptionalValue("taktx.client.enabled", Boolean.class))
        .thenReturn(Optional.of(false));

    provider.init();

    assertThat(provider.taktClient()).isNull();
    assertThat(provider.newClientBuilderCalls).isZero();
  }

  @Test
  void testTaktClient_returnsStoredInstance() {
    TaktXClient mockClient = mock(TaktXClient.class);
    setStaticTaktClient(mockClient);

    TaktXClient result = provider.taktClient();

    assertThat(result).isSameAs(mockClient);
  }

  @Test
  void testTaktClient_whenNotInitialized_returnsNull() {
    TaktXClient result = provider.taktClient();

    assertThat(result).isNull();
  }

  @Test
  void testConstructor_storesAllDependencies() {
    assertThat(provider).isNotNull();
  }

  @Test
  void resolveParticipantDescriptor_usesApplicationNameAndWorkerCapabilities() {
    when(config.getOptionalValue("quarkus.application.name", String.class))
        .thenReturn(Optional.of("orders-console"));

    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "payments");

    SecurityParticipantDescriptor descriptor = provider.resolveParticipantDescriptor(properties);

    assertThat(descriptor.participantId()).isEqualTo("payments.orders-console");
    assertThat(descriptor.kind()).isEqualTo(ParticipantKind.CLIENT);
    assertThat(descriptor.componentType()).isEqualTo("orders-console");
    assertThat(descriptor.capabilities())
        .containsExactly(
            ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
            ParticipantCapability.SECURITY_OBSERVER);
  }

  @Test
  void resolveParticipantDescriptor_fallsBackWhenApplicationNameMissing() {
    when(config.getOptionalValue("quarkus.application.name", String.class))
        .thenReturn(Optional.empty());

    Properties properties = defaultProperties();

    SecurityParticipantDescriptor descriptor = provider.resolveParticipantDescriptor(properties);

    assertThat(descriptor.participantId()).isEqualTo("payments.generic-client");
    assertThat(descriptor.componentType()).isEqualTo("generic-client");
    assertThat(descriptor.kind()).isEqualTo(ParticipantKind.CLIENT);
  }

  @Test
  void init_whenEnabled_registersExternalTaskConsumerWhenJobsExist() {
    when(config.getOptionalValue("taktx.client.enabled", Boolean.class))
        .thenReturn(Optional.of(true));
    when(config.getOptionalValue("quarkus.application.name", String.class))
        .thenReturn(Optional.of("orders-console"));
    when(observerChecker.hasInstanceUpdateRecordObservers()).thenReturn(false);
    when(externalTaskTriggerConsumer.getJobIds()).thenReturn(Set.of("service-task"));

    provider.init();

    assertThat(provider.newClientBuilderCalls).isEqualTo(1);
    assertThat(provider.startCalled).isTrue();
    assertThat(provider.deployAnnotatedClassesCalled).isTrue();
    assertThat(provider.externalTaskConsumerRegistered).isTrue();
    assertThat(provider.instanceUpdateConsumerRegistered).isFalse();
    assertThat(provider.taktClient()).isSameAs(client);
  }

  @Test
  void init_whenEnabled_skipsExternalTaskRegistrationWhenNoJobsExist() {
    when(config.getOptionalValue("taktx.client.enabled", Boolean.class))
        .thenReturn(Optional.of(true));
    when(config.getOptionalValue("quarkus.application.name", String.class))
        .thenReturn(Optional.of("orders-console"));
    when(observerChecker.hasInstanceUpdateRecordObservers()).thenReturn(false);
    when(externalTaskTriggerConsumer.getJobIds()).thenReturn(Collections.emptySet());

    provider.init();

    assertThat(provider.externalTaskConsumerRegistered).isFalse();
  }

  @Test
  void init_whenEnabled_registersInstanceUpdateConsumerAndFiresEvents() {
    when(config.getOptionalValue("taktx.client.enabled", Boolean.class))
        .thenReturn(Optional.of(true));
    when(config.getOptionalValue("quarkus.application.name", String.class))
        .thenReturn(Optional.of("orders-console"));
    when(observerChecker.hasInstanceUpdateRecordObservers()).thenReturn(true);
    setInstanceUpdateStartStrategy("earliest");
    provider.emittedRecords =
        List.of(
            new InstanceUpdateRecord(1L, null, null, 0, 0L),
            new InstanceUpdateRecord(2L, null, null, 1, 1L));

    provider.init();

    assertThat(provider.instanceUpdateConsumerRegistered).isTrue();
    assertThat(provider.capturedInstanceUpdateStrategy)
        .isEqualTo(InstanceUpdateStartStrategy.EARLIEST);
    verify(events).fire(provider.emittedRecords.get(0));
    verify(events).fire(provider.emittedRecords.get(1));
  }

  @Test
  void init_whenStaticClientAlreadyInitialized_skipsBuilderWork() {
    TaktXClient existingClient = mock(TaktXClient.class);
    setStaticTaktClient(existingClient);
    when(config.getOptionalValue("taktx.client.enabled", Boolean.class))
        .thenReturn(Optional.of(true));

    provider.init();

    assertThat(provider.newClientBuilderCalls).isZero();
    assertThat(provider.startCalled).isFalse();
    assertThat(provider.taktClient()).isSameAs(existingClient);
    verify(observerChecker, never()).hasInstanceUpdateRecordObservers();
  }

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

  private void setInstanceUpdateStartStrategy(String strategy) {
    try {
      java.lang.reflect.Field field =
          TaktXClientProvider.class.getDeclaredField("instanceUpdateStartStrategy");
      field.setAccessible(true);
      field.set(provider, strategy);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set instanceUpdateStartStrategy field", e);
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

  private static Properties defaultProperties() {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "payments");
    return properties;
  }

  private static final class TestableTaktXClientProvider extends TaktXClientProvider {

    private final TaktXClientBuilder builder;
    private final AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer;

    private Properties builtProperties;
    private int newClientBuilderCalls;
    private boolean startCalled;
    private boolean deployAnnotatedClassesCalled;
    private boolean externalTaskConsumerRegistered;
    private boolean instanceUpdateConsumerRegistered;
    private InstanceUpdateStartStrategy capturedInstanceUpdateStrategy;
    private List<InstanceUpdateRecord> emittedRecords = List.of();

    private TestableTaktXClientProvider(
        Config config,
        InstanceUpdateRecordObserverChecker observerChecker,
        Event<InstanceUpdateRecord> events,
        WorkerBeanInstanceProvider instanceProvider,
        ParameterResolverFactory parameterResolverFactory,
        ResultProcessorFactory resultProcessorFactory,
        TaktXClientBuilder builder,
        AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer) {
      super(
          config,
          observerChecker,
          events,
          instanceProvider,
          parameterResolverFactory,
          resultProcessorFactory);
      this.builder = builder;
      this.externalTaskTriggerConsumer = externalTaskTriggerConsumer;
    }

    @Override
    TaktXClientBuilder newClientBuilder() {
      newClientBuilderCalls++;
      return builder;
    }

    @Override
    Properties buildTaktProperties() {
      return builtProperties;
    }

    @Override
    void startClient(TaktXClient client) {
      startCalled = true;
    }

    @Override
    void deployAnnotatedClasses(TaktXClient client) {
      deployAnnotatedClassesCalled = true;
    }

    @Override
    AnnotationScanningExternalTaskTriggerConsumer createExternalTaskTriggerConsumer(
        TaktXClient client) {
      return externalTaskTriggerConsumer;
    }

    @Override
    void registerExternalTaskConsumer(
        TaktXClient client,
        AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer) {
      externalTaskConsumerRegistered = true;
    }

    @Override
    void registerInstanceUpdateConsumer(
        TaktXClient client,
        InstanceUpdateStartStrategy strategy,
        java.util.function.Consumer<List<InstanceUpdateRecord>> instanceUpdateConsumer) {
      instanceUpdateConsumerRegistered = true;
      capturedInstanceUpdateStrategy = strategy;
      instanceUpdateConsumer.accept(emittedRecords);
    }
  }
}
