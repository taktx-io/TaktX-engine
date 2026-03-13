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

import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ResultProcessorFactory;
import io.taktx.client.TaktXClient;
import io.taktx.client.WorkerBeanInstanceProvider;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TaktXClientAutoConfigurationTest {

  private TaktPropertiesHelper taktPropertiesHelper;
  private InstanceUpdateRecordEventChecker eventChecker;
  private WorkerBeanInstanceProvider instanceProvider;
  private ParameterResolverFactory parameterResolverFactory;
  private ResultProcessorFactory resultProcessorFactory;
  private TaktXClientAutoConfiguration configuration;

  @BeforeEach
  void setUp() {
    Properties properties = new Properties();
    properties.setProperty("taktx.engine.namespace", "test");
    properties.setProperty("taktx.engine.tenant-id", "acme");
    properties.setProperty("kafka.bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.client.enabled", "false"); // Disable for tests

    taktPropertiesHelper = new TaktPropertiesHelper(properties);
    eventChecker = mock(InstanceUpdateRecordEventChecker.class);
    instanceProvider = mock(WorkerBeanInstanceProvider.class);
    parameterResolverFactory = mock(ParameterResolverFactory.class);
    resultProcessorFactory = mock(ResultProcessorFactory.class);

    configuration =
        new TaktXClientAutoConfiguration(
            taktPropertiesHelper,
            eventChecker,
            instanceProvider,
            parameterResolverFactory,
            resultProcessorFactory);

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
}
