/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import io.quarkus.runtime.Startup;
import io.taktx.CleanupPolicy;
import io.taktx.client.AnnotationScanningExternalTaskTriggerConsumer;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.InstanceUpdateStartStrategy;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ResultProcessorFactory;
import io.taktx.client.TaktXClient;
import io.taktx.client.TaktXClient.TaktXClientBuilder;
import io.taktx.client.WorkerBeanInstanceProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import java.util.Properties;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Provides a singleton TaktXClient instance for the application, initialized at startup with
 * configuration from MicroProfile Config.
 */
@ApplicationScoped
@Startup
public class TaktXClientProvider {
  private static TaktXClient taktClient;

  // Inject the full MicroProfile Config so we can read all application properties
  private final Config config;
  private final InstanceUpdateRecordObserverChecker observerChecker;
  private final Event<InstanceUpdateRecord> events;
  private final WorkerBeanInstanceProvider instanceProvider;
  private final ParameterResolverFactory parameterResolverFactory;
  private final ResultProcessorFactory resultProcessorFactory;

  @ConfigProperty(name = "taktx.engine.topic.partitions", defaultValue = "3")
  int partitions;

  @ConfigProperty(name = "taktx.engine.topic.replicationFactor", defaultValue = "1")
  short replicationFactor;

  @ConfigProperty(name = "taktx.client.groupId.instanceupdate")
  String groupIdInstanceUpdate;

  /**
   * Controls where the instance-update consumer begins reading.
   *
   * <ul>
   *   <li>{@code RESUME} (default) — continue from the last committed offset for this group.
   *   <li>{@code EARLIEST} — seek to offset 0 on every assigned partition after the initial
   *       rebalance, guaranteeing a full-history replay regardless of prior committed offsets.
   * </ul>
   *
   * <p>Property: {@code taktx.client.instanceupdate.start-strategy}
   */
  @ConfigProperty(name = "taktx.client.instanceupdate.start-strategy", defaultValue = "RESUME")
  String instanceUpdateStartStrategy;

  /**
   * Constructor injecting the MicroProfile Config.
   *
   * @param config the MicroProfile Config instance
   * @param observerChecker the ObserverChecker to check for CDI observers
   * @param events the CDI Event to fire InstanceUpdateRecords
   * @param instanceProvider the WorkerBeanInstanceProvider for bean instances
   * @param parameterResolverFactory the ParameterResolverFactory to use
   * @param resultProcessorFactory the ResultProcessorFactory to use
   */
  public TaktXClientProvider(
      Config config,
      InstanceUpdateRecordObserverChecker observerChecker,
      Event<InstanceUpdateRecord> events,
      WorkerBeanInstanceProvider instanceProvider,
      ParameterResolverFactory parameterResolverFactory,
      ResultProcessorFactory resultProcessorFactory) {
    this.config = config;
    this.observerChecker = observerChecker;
    this.events = events;
    this.instanceProvider = instanceProvider;
    this.parameterResolverFactory = parameterResolverFactory;
    this.resultProcessorFactory = resultProcessorFactory;
  }

  @PostConstruct
  void init() {
    // allow tests to disable the real TaktXClient by setting taktX.client.enabled=false
    boolean clientEnabled =
        config.getOptionalValue("taktx.client.enabled", Boolean.class).orElse(true);
    if (!clientEnabled) {
      // skip initialization in test-mode
      System.out.println(
          "TaktXClientProvider: taktX.client.enabled=false, skipping TaktXClient startup (test mode)");
      return;
    }
    TaktXClientBuilder taktClientBuilder = TaktXClient.newClientBuilder();

    synchronized (TaktXClientProvider.class) {
      if (taktClient == null) {
        // Build a Properties object containing all application properties. This lets us pass the
        // full application config to the TaktXClient so it can override sensible defaults.
        Properties taktProperties = new Properties();

        // Copy all available config entries into the Properties object as Strings
        for (String name : config.getPropertyNames()) {
          config
              .getOptionalValue(name, String.class)
              .ifPresent(value -> taktProperties.put(name, value));
        }
        taktClientBuilder
            .withTaktParameterResolverFactory(parameterResolverFactory)
            .withResultProcessorFactory(resultProcessorFactory);

        taktClient = taktClientBuilder.withProperties(taktProperties).build();
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

        if (observerChecker.hasInstanceUpdateRecordObservers()) {
          InstanceUpdateStartStrategy strategy =
              InstanceUpdateStartStrategy.valueOf(instanceUpdateStartStrategy.toUpperCase());
          taktClient.registerInstanceUpdateConsumer(
              groupIdInstanceUpdate,
              instanceUpdateRecords -> {
                for (InstanceUpdateRecord instanceUpdateRecord : instanceUpdateRecords) {
                  events.fire(instanceUpdateRecord);
                }
              },
              strategy);
        }
      }
    }
  }

  /**
   * Produces the singleton TaktXClient instance for injection.
   *
   * @return the TaktXClient instance
   */
  @Produces
  public TaktXClient taktClient() {
    return taktClient;
  }
}
