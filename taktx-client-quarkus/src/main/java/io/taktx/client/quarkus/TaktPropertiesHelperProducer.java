/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
