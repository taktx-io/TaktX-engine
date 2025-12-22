/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import io.taktx.CleanupPolicy;
import io.taktx.client.AnnotationScanningExternalTaskTriggerConsumer;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ResultProcessorFactory;
import io.taktx.client.TaktXClient;
import io.taktx.client.TaktXClient.TaktXClientBuilder;
import io.taktx.client.WorkerBeanInstanceProvider;
import io.taktx.util.TaktPropertiesHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for TaktXClient in Spring Boot applications. Creates and configures a
 * TaktXClient instance with all necessary dependencies.
 */
@Configuration
@ConditionalOnProperty(name = "taktx.client.enabled", havingValue = "true", matchIfMissing = true)
public class TaktXClientAutoConfiguration {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final InstanceUpdateRecordEventChecker eventChecker;
  private final WorkerBeanInstanceProvider instanceProvider;
  private final ParameterResolverFactory parameterResolverFactory;
  private final ResultProcessorFactory resultProcessorFactory;

  @Value("${taktx.engine.topic.partitions:3}")
  private int partitions;

  @Value("${taktx.engine.topic.replicationFactor:1}")
  private short replicationFactor;

  @Value("${taktx.client.groupId.instanceupdate:}")
  private String groupIdInstanceUpdate;

  @Value("${taktx.client.instanceupdate.enabled:false}")
  private boolean instanceUpdateEnabled;

  private TaktXClient taktClient;

  /**
   * Constructor injecting all required dependencies.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper instance
   * @param eventChecker the InstanceUpdateRecordEventChecker to check for event listeners
   * @param instanceProvider the WorkerBeanInstanceProvider for bean instances
   * @param parameterResolverFactory the ParameterResolverFactory to use
   * @param resultProcessorFactory the ResultProcessorFactory to use
   */
  public TaktXClientAutoConfiguration(
      TaktPropertiesHelper taktPropertiesHelper,
      InstanceUpdateRecordEventChecker eventChecker,
      WorkerBeanInstanceProvider instanceProvider,
      ParameterResolverFactory parameterResolverFactory,
      ResultProcessorFactory resultProcessorFactory) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.eventChecker = eventChecker;
    this.instanceProvider = instanceProvider;
    this.parameterResolverFactory = parameterResolverFactory;
    this.resultProcessorFactory = resultProcessorFactory;
  }

  /** Initializes the TaktXClient after construction. */
  @PostConstruct
  public void init() {
    TaktXClientBuilder taktClientBuilder = TaktXClient.newClientBuilder();

    taktClientBuilder
        .withTaktParameterResolverFactory(parameterResolverFactory)
        .withResultProcessorFactory(resultProcessorFactory);

    taktClient = taktClientBuilder.withProperties(taktPropertiesHelper.getTaktProperties()).build();

    taktClient.start();

    taktClient.deployTaktDeploymentAnnotatedClasses();

    AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer =
        new AnnotationScanningExternalTaskTriggerConsumer(
            taktClient.getParameterResolverFactory(),
            taktClient.getResultProcessorFactory(),
            taktClient.getProcessInstanceResponder(),
            instanceProvider,
            taktClient.getExternalTaskTopicRequester(),
            partitions,
            CleanupPolicy.COMPACT,
            replicationFactor);

    if (!externalTaskTriggerConsumer.getJobIds().isEmpty()) {
      taktClient.registerExternalTaskConsumer(
          externalTaskTriggerConsumer, "taktx-client-external-task-trigger-consumer");
    }

    if (instanceUpdateEnabled
        && groupIdInstanceUpdate != null
        && !groupIdInstanceUpdate.isEmpty()) {
      taktClient.registerInstanceUpdateConsumer(
          groupIdInstanceUpdate,
          instanceUpdateRecords -> {
            for (var instanceUpdateRecord : instanceUpdateRecords) {
              eventChecker.publishInstanceUpdateRecord(instanceUpdateRecord);
            }
          });
    }
  }

  /**
   * Provides the TaktXClient bean for injection.
   *
   * @return the TaktXClient instance
   */
  @Bean
  @ConditionalOnMissingBean
  public TaktXClient taktXClient() {
    return taktClient;
  }
}
