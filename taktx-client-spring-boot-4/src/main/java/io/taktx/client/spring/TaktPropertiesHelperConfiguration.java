/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/** Provides a TaktPropertiesHelper bean for Spring Boot 4 applications. */
@Configuration
public class TaktPropertiesHelperConfiguration {

  /**
   * Provides a TaktPropertiesHelper bean from Spring Environment.
   *
   * @param environment the Spring Environment
   * @return a TaktPropertiesHelper instance
   */
  @Bean
  @ConditionalOnMissingBean
  public TaktPropertiesHelper taktPropertiesHelper(ConfigurableEnvironment environment) {
    Properties properties = new Properties();

    // Extract all properties from Spring Environment using pattern-matching instanceof (Java 16+)
    for (var propertySource : environment.getPropertySources()) {
      if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
        for (String name : enumerablePropertySource.getPropertyNames()) {
          Object value = environment.getProperty(name);
          if (value != null) {
            properties.setProperty(name, value.toString());
          }
        }
      }
    }

    return new TaktPropertiesHelper(properties);
  }
}
