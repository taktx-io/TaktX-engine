/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app;

import io.quarkus.runtime.Startup;
import io.taktx.CleanupPolicy;
import io.taktx.client.AnnotationScanningExternalTaskTriggerConsumer;
import io.taktx.client.TaktClient;
import io.taktx.client.TaktClient.TaktClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Startup
@Slf4j
@Priority(Integer.MAX_VALUE) // Lower value means higher priority
public class TaktClientProvider {
  private static TaktClient taktClient;

  public TaktClientProvider() {
    log.info("TaktClientProvider instantiated");
  }

  @PostConstruct
  void init() throws IOException {
    TaktClientBuilder taktClientBuilder = TaktClient.newClientBuilder();
    synchronized (TaktClientProvider.class) {
      if (taktClient == null) {
        Properties properties = new Properties();

        String taktPropertiesFile = System.getenv("TAKTX_PROPERTIES_FILE");
        log.info("TaktClientProvider taktPropertiesFile: {}", taktPropertiesFile);
        try (FileInputStream fileInputStream = new FileInputStream(taktPropertiesFile)) {
          properties.load(fileInputStream);
        }

        taktClient =
            taktClientBuilder
                .withTenant(properties.getProperty("taktx.engine.tenant"))
                .withNamespace(properties.getProperty("taktx.engine.namespace"))
                .withKafkaProperties(properties)
                .build();
        taktClient.start();
        taktClient.deployTaktDeploymentAnnotatedClasses();
        AnnotationScanningExternalTaskTriggerConsumer externalTaskTriggerConsumer =
            new AnnotationScanningExternalTaskTriggerConsumer(
                taktClient.getParameterResolverFactory(), taktClient.getProcessInstanceResponder());
        taktClient.registerExternalTaskConsumer(externalTaskTriggerConsumer);
        externalTaskTriggerConsumer
            .getJobIds()
            .forEach(jobId -> taktClient.requestExternalTaskTopic(jobId, 3, CleanupPolicy.COMPACT));
      }
    }
  }

  @Produces
  TaktClient taktClient() {
    return taktClient;
  }
}
