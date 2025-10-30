/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import io.quarkus.runtime.Startup;
import io.taktx.CleanupPolicy;
import io.taktx.client.AnnotationScanningExternalTaskTriggerConsumer;
import io.taktx.client.TaktClient;
import io.taktx.client.TaktClient.TaktClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.Properties;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
@Priority(Integer.MAX_VALUE) // Lower value means higher priority
public class TaktClientProvider {
  private static TaktClient taktClient;

  // Inject the full MicroProfile Config so we can read all application properties
  private final Config config;

  @ConfigProperty(name = "taktx.engine.topic.partitions", defaultValue = "3")
  int partitions;

  @ConfigProperty(name = "taktx.engine.topic.replicationFactor", defaultValue = "1")
  short replicationFactor;

  public TaktClientProvider(Config config) {
    this.config = config;
  }

  @PostConstruct
  void init() {
    TaktClientBuilder taktClientBuilder = TaktClient.newClientBuilder();

    synchronized (TaktClientProvider.class) {
      if (taktClient == null) {
        // Build a Properties object containing all application properties. This lets us pass the
        // full application config to the TaktClient so it can override sensible defaults.
        Properties taktProperties = new Properties();

        // Copy all available config entries into the Properties object as Strings
        for (String name : config.getPropertyNames()) {
          config
              .getOptionalValue(name, String.class)
              .ifPresent(value -> taktProperties.put(name, value));
        }

        taktClient = taktClientBuilder.withTaktProperties(taktProperties).build();
        taktClient.start();

        taktClient.deployTaktDeploymentAnnotatedClasses();

        AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer =
            new AnnotationScanningExternalTaskTriggerConsumer(
                taktClient.getParameterResolverFactory(), taktClient.getProcessInstanceResponder());

        taktClient.registerExternalTaskConsumer(
            externalTaskTriggerConsumer, "taktx-client-external-task-trigger-consumer");

        externalTaskTriggerConsumer
            .getJobIds()
            .forEach(
                jobId ->
                    taktClient.requestExternalTaskTopic(
                        jobId, partitions, CleanupPolicy.COMPACT, replicationFactor));
      }
    }
  }

  @Produces
  public TaktClient taktClient() {
    return taktClient;
  }
}
