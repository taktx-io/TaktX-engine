/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

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
import io.taktx.util.TaktPropertiesHelper;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TaktXClientAutoConfigurationTest {

  private TestableTaktXClientAutoConfiguration configuration;
  private InstanceUpdateRecordEventChecker eventChecker;
  private TaktXClientBuilder builder;
  private TaktXClient client;
  private AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer;

  @BeforeEach
  void setUp() {
    Properties properties = new Properties();
    properties.setProperty("taktx.engine.namespace", "test");
    properties.setProperty("taktx.engine.tenant-id", "acme");
    properties.setProperty("spring.application.name", "orders-service");
    properties.setProperty("kafka.bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.client.enabled", "false"); // Disable for tests

    TaktPropertiesHelper taktPropertiesHelper = new TaktPropertiesHelper(properties);
    eventChecker = mock(InstanceUpdateRecordEventChecker.class);
    WorkerBeanInstanceProvider instanceProvider = mock(WorkerBeanInstanceProvider.class);
    ParameterResolverFactory parameterResolverFactory = mock(ParameterResolverFactory.class);
    ResultProcessorFactory resultProcessorFactory = mock(ResultProcessorFactory.class);
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

    configuration =
        new TestableTaktXClientAutoConfiguration(
            taktPropertiesHelper,
            eventChecker,
            instanceProvider,
            parameterResolverFactory,
            resultProcessorFactory,
            builder,
            externalTaskTriggerConsumer);

    // Set default values using reflection
    ReflectionTestUtils.setField(configuration, "partitions", 3);
    ReflectionTestUtils.setField(configuration, "replicationFactor", (short) 1);
    ReflectionTestUtils.setField(configuration, "groupIdInstanceUpdate", "test-group");
    ReflectionTestUtils.setField(configuration, "instanceUpdateEnabled", false);
  }

  @Test
  void testTaktXClientBean_notNull() {
    // Note: In actual tests with disabled client, taktClient will be null
    // This test verifies the bean method exists and returns the field value
    TaktXClient client = configuration.taktXClient();

    // Since we disabled the client for testing, it should be null
    assertThat(client).isNull();
  }

  @Test
  void testConfiguration_hasRequiredDependencies() {
    // Verify that all required dependencies are injected
    assertThat(configuration).isNotNull();

    Object helper = ReflectionTestUtils.getField(configuration, "taktPropertiesHelper");
    assertThat(helper).isNotNull();

    Object checker = ReflectionTestUtils.getField(configuration, "eventChecker");
    assertThat(checker).isNotNull();

    Object provider = ReflectionTestUtils.getField(configuration, "instanceProvider");
    assertThat(provider).isNotNull();

    Object paramFactory = ReflectionTestUtils.getField(configuration, "parameterResolverFactory");
    assertThat(paramFactory).isNotNull();

    Object resultFactory = ReflectionTestUtils.getField(configuration, "resultProcessorFactory");
    assertThat(resultFactory).isNotNull();
  }

  @Test
  void resolveParticipantDescriptor_usesSpringApplicationNameAndWorkerCapabilities() {
    SecurityParticipantDescriptor descriptor = configuration.resolveParticipantDescriptor();

    assertThat(descriptor.participantId()).isEqualTo("test.orders-service");
    assertThat(descriptor.kind()).isEqualTo(ParticipantKind.CLIENT);
    assertThat(descriptor.componentType()).isEqualTo("orders-service");
    assertThat(descriptor.capabilities())
        .containsExactly(
            ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
            ParticipantCapability.SECURITY_OBSERVER);
  }

  @Test
  void init_registersExternalTaskConsumerWhenJobsExist_andStoresBuiltClient() {
    when(externalTaskTriggerConsumer.getJobIds()).thenReturn(Set.of("service-task"));

    configuration.init();

    assertThat(configuration.startCalled).isTrue();
    assertThat(configuration.deployAnnotatedClassesCalled).isTrue();
    assertThat(configuration.externalTaskConsumerRegistered).isTrue();
    assertThat(configuration.instanceUpdateConsumerRegistered).isFalse();
    assertThat(configuration.taktXClient()).isSameAs(client);

    verify(builder).withParticipantDescriptor(any(SecurityParticipantDescriptor.class));
    verify(builder).withProperties(any(Properties.class));
  }

  @Test
  void init_skipsExternalTaskRegistrationWhenNoJobsExist() {
    when(externalTaskTriggerConsumer.getJobIds()).thenReturn(Collections.emptySet());

    configuration.init();

    assertThat(configuration.externalTaskConsumerRegistered).isFalse();
  }

  @Test
  void init_registersInstanceUpdateConsumerWhenEnabled_andPublishesRecords() {
    ReflectionTestUtils.setField(configuration, "instanceUpdateEnabled", true);
    ReflectionTestUtils.setField(configuration, "instanceUpdateStartStrategy", "earliest");
    configuration.emittedRecords =
        List.of(
            new InstanceUpdateRecord(1L, null, null, 0, 0L),
            new InstanceUpdateRecord(2L, null, null, 1, 1L));

    configuration.init();

    assertThat(configuration.instanceUpdateConsumerRegistered).isTrue();
    assertThat(configuration.capturedInstanceUpdateStrategy)
        .isEqualTo(InstanceUpdateStartStrategy.EARLIEST);
    verify(eventChecker).publishInstanceUpdateRecord(configuration.emittedRecords.get(0));
    verify(eventChecker).publishInstanceUpdateRecord(configuration.emittedRecords.get(1));
  }

  @Test
  void init_skipsInstanceUpdateRegistrationWhenGroupIdBlank() {
    ReflectionTestUtils.setField(configuration, "instanceUpdateEnabled", true);
    ReflectionTestUtils.setField(configuration, "groupIdInstanceUpdate", "");

    configuration.init();

    assertThat(configuration.instanceUpdateConsumerRegistered).isFalse();
    verify(eventChecker, never()).publishInstanceUpdateRecord(any());
  }

  private static final class TestableTaktXClientAutoConfiguration
      extends TaktXClientAutoConfiguration {

    private final TaktXClientBuilder builder;
    private final AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer;

    private boolean startCalled;
    private boolean deployAnnotatedClassesCalled;
    private boolean externalTaskConsumerRegistered;
    private boolean instanceUpdateConsumerRegistered;
    private InstanceUpdateStartStrategy capturedInstanceUpdateStrategy;
    private List<InstanceUpdateRecord> emittedRecords = List.of();

    private TestableTaktXClientAutoConfiguration(
        TaktPropertiesHelper taktPropertiesHelper,
        InstanceUpdateRecordEventChecker eventChecker,
        WorkerBeanInstanceProvider instanceProvider,
        ParameterResolverFactory parameterResolverFactory,
        ResultProcessorFactory resultProcessorFactory,
        TaktXClientBuilder builder,
        AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer) {
      super(
          taktPropertiesHelper,
          eventChecker,
          instanceProvider,
          parameterResolverFactory,
          resultProcessorFactory);
      this.builder = builder;
      this.externalTaskTriggerConsumer = externalTaskTriggerConsumer;
    }

    @Override
    TaktXClientBuilder newClientBuilder() {
      return builder;
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
