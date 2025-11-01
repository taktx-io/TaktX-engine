/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app;

import io.quarkus.runtime.Startup;
import io.taktx.client.TaktXClient;
import io.taktx.client.TaktXClient.TaktXClientBuilder;
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
public class TaktXClientProvider {
  private static TaktXClient taktClient;

  public TaktXClientProvider() {
    log.info("TaktXClientProvider instantiated");
  }

  @PostConstruct
  void init() throws IOException {
    TaktXClientBuilder taktClientBuilder = TaktXClient.newClientBuilder();
    synchronized (TaktXClientProvider.class) {
      if (taktClient == null) {
        Properties properties = new Properties();

        String taktPropertiesFile = System.getenv("TAKTX_PROPERTIES_FILE");
        log.info("TaktXClientProvider taktPropertiesFile: {}", taktPropertiesFile);
        try (FileInputStream fileInputStream = new FileInputStream(taktPropertiesFile)) {
          properties.load(fileInputStream);
        }

        taktClient = taktClientBuilder.withProperties(properties).build();
        taktClient.start();
      }
    }
  }

  @Produces
  TaktXClient taktClient() {
    return taktClient;
  }
}
