/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import io.taktx.util.TaktPropertiesHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.Properties;
import org.eclipse.microprofile.config.Config;

/** Produces a TaktPropertiesHelper bean for Quarkus applications. */
public class TaktPropertiesHelperProducer {

  private final Config config;

  /**
   * Constructor injecting the MicroProfile Config.
   *
   * @param config the MicroProfile Config to be used
   */
  public TaktPropertiesHelperProducer(Config config) {
    this.config = config;
  }

  /**
   * Produces a TaktPropertiesHelper bean.
   *
   * @return a TaktPropertiesHelper instance
   */
  @Produces
  @ApplicationScoped
  public TaktPropertiesHelper taktPropertiesHelper() {
    Properties properties = new Properties();
    for (String name : config.getPropertyNames()) {
      config
          .getOptionalValue(name, String.class)
          .ifPresent(value -> properties.setProperty(name, value));
    }
    return new TaktPropertiesHelper(properties);
  }
}
